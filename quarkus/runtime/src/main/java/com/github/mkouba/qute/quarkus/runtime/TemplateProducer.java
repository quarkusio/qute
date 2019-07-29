package com.github.mkouba.qute.quarkus.runtime;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mkouba.qute.Engine;
import com.github.mkouba.qute.EngineBuilder;
import com.github.mkouba.qute.IfSectionHelper;
import com.github.mkouba.qute.IncludeSectionHelper;
import com.github.mkouba.qute.InsertSectionHelper;
import com.github.mkouba.qute.LoopSectionHelper;
import com.github.mkouba.qute.NamespaceResolver;
import com.github.mkouba.qute.Results.Result;
import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.Template.Rendering;
import com.github.mkouba.qute.UserTagSectionHelper;
import com.github.mkouba.qute.ValueResolver;
import com.github.mkouba.qute.WithSectionHelper;
import com.github.mkouba.qute.quarkus.Located;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

@Singleton
public class TemplateProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateProducer.class);

    static final String METAINF_RESOURCES = "META-INF/resources/";
    static final String TAG_RESOURCES = METAINF_RESOURCES + "tags/";

    @Inject
    Event<EngineBuilder> event;

    private Engine engine;
    private List<String> tags;

    Engine init(List<String> resolverClasses, List<String> templatePaths, List<String> tags) {
        if (engine != null) {
            LOGGER.warn("Qute already initialized!");
            return engine;
        }
        LOGGER.debug("Initializing Qute with: {}", resolverClasses);
        EngineBuilder builder = Engine.builder()
                .addSectionHelpers(new LoopSectionHelper.Factory(), new IfSectionHelper.Factory(),
                        new WithSectionHelper.Factory(), new IncludeSectionHelper.Factory(), new InsertSectionHelper.Factory());
        // Allow anyone to customize the builder
        event.fire(builder);
        // Resolve @Named beans
        builder.addNamespaceResolver(NamespaceResolver.builder("inject").resolve(ctx -> {
            InstanceHandle<Object> bean = Arc.container().instance(ctx.getName());
            return bean.isAvailable() ? bean.get() : Result.NOT_FOUND;
        }).build());
        // Basic value resolvers
        builder.addDefaultValueResolvers();
        // Add generated resolvers
        for (String resolverClass : resolverClasses) {
            builder.addValueResolver(createResolver(resolverClass));
            LOGGER.debug("Added generated value resolver: {}", resolverClass);
        }
        // Add tags
        this.tags = tags;
        for (String tag : tags) {
            // Strip suffix, item.html -> item
            String tagName = tag.contains(".") ? tag.substring(0, tag.lastIndexOf('.')) : tag;
            LOGGER.debug("Registered UserTagSectionHelper for {}", tagName);
            builder.addSectionHelper(new UserTagSectionHelper.Factory(tagName));
        }
        // Add locator
        builder.addLocator(this::locate);
        engine = builder.build();

        // Load discovered templates
        for (String path : templatePaths) {
            engine.getTemplate(path);
        }
        return engine;
    }

    ValueResolver createResolver(String resolverClassName) {
        try {
            Class<?> resolverClazz = Thread.currentThread()
                    .getContextClassLoader().loadClass(resolverClassName);
            if (ValueResolver.class.isAssignableFrom(resolverClazz)) {
                return (ValueResolver) resolverClazz.newInstance();
            }
            throw new IllegalStateException("Not a value resolver: " + resolverClassName);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to create resolver: " + resolverClassName, e);
        }
    }

    @Produces
    @Located
    Template getTemplate(InjectionPoint injectionPoint) {
        Located path = null;
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(Located.class)) {
                path = (Located) qualifier;
                break;
            }
        }
        if (path == null) {
            throw new IllegalStateException();
        }
        // Note that engine may not be initialized and so we inject a delegating template
        if (path.value().isEmpty()) {
            // For "@Inject Template items" use "items"
            return new InjectableTemplate(this::getEngine, injectionPoint.getMember().getName());
        } else {
            return new InjectableTemplate(this::getEngine, path.value());
        }
    }

    @Produces
    @ApplicationScoped
    Engine getEngine() {
        return engine;
    }

    /**
     * For any path we try to find the following resources:
     * 
     * <ol>
     * <li>META-INF/resources/path</li>
     * <li>META-INF/resources/path.html</li>
     * </ol>
     * 
     * @param path
     * @return the optional reader
     */
    private Optional<Reader> locate(String path) {
        InputStream in = null;
        // First try to locate a tag template
        if (tags.stream().anyMatch(tag -> tag.startsWith(path))) {
            in = locatePath(TAG_RESOURCES + path);
            if (in == null) {
                in = locatePath(TAG_RESOURCES + path + ".html");
            }
        }
        if (in == null) {
            // Try {path} and {path}.html
            in = locatePath(METAINF_RESOURCES + path);
            if (in == null) {
                in = locatePath(METAINF_RESOURCES + path + ".html");
            }
        }

        if (in != null) {
            return Optional.of(new InputStreamReader(in, Charset.forName("utf-8")));
        }
        return Optional.empty();
    }

    private InputStream locatePath(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = TemplateProducer.class.getClassLoader();
        }
        return cl.getResourceAsStream(path);
    }

    static class InjectableTemplate implements Template {

        private final String path;
        private final Supplier<Engine> engine;

        public InjectableTemplate(Supplier<Engine> engine, String path) {
            this.engine = engine;
            this.path = path;
        }
        
        @Override
        public Rendering render() {
            return delegate().render();
        }

        Template delegate() {
            Template template = engine.get().getTemplate(path);
            if (template == null) {
                throw new IllegalStateException("No template found for path: " + path);
            }
            return template;
        }

    }

}
