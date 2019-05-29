package com.github.mkouba.qute.quarkus.runtime;

import static com.github.mkouba.qute.ValueResolvers.collectionResolver;
import static com.github.mkouba.qute.ValueResolvers.mapResolver;
import static com.github.mkouba.qute.ValueResolvers.orResolver;
import static com.github.mkouba.qute.ValueResolvers.thisResolver;

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
import com.github.mkouba.qute.ValueResolver;
import com.github.mkouba.qute.WithSectionHelper;
import com.github.mkouba.qute.quarkus.TemplatePath;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

@Singleton
public class TemplateProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateProducer.class);

    @Inject
    Event<EngineBuilder> event;

    private Engine engine;

    void init(List<String> resolverClasses, List<String> templatePaths) {
        if (engine != null) {
            LOGGER.warn("Qute already initialized!");
            return;
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
        builder.addValueResolvers(mapResolver(), collectionResolver(),
                thisResolver(), orResolver());
        // Add generated resolvers
        for (String resolverClass : resolverClasses) {
            builder.addValueResolver(createResolver(resolverClass));
            LOGGER.debug("Added generated value resolver: {}", resolverClass);
        }
        // Add locator
        builder.addLocator(this::locate);
        engine = builder.build();

        // Load discovered templates
        for (String path : templatePaths) {
            engine.getTemplate(path);
        }
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
    @TemplatePath
    Template getTemplate(InjectionPoint injectionPoint) {
        TemplatePath path = null;
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(TemplatePath.class)) {
                path = (TemplatePath) qualifier;
                break;
            }
        }
        if (path == null) {
            throw new IllegalStateException();
        }
        // Note that engine may not be initialized and so we inject a delegating template 
        return new InjectableTemplate(this::getEngine, path.value());
    }

    @Produces
    @ApplicationScoped
    Engine getEngine() {
        return engine;
    }

    private Optional<Reader> locate(String path) {
        String resource = "META-INF/resources/" + path;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = TemplateProducer.class.getClassLoader();
        }
        InputStream in = cl.getResourceAsStream(resource);
        if (in == null) {
            return Optional.empty();
        }
        return Optional.of(new InputStreamReader(in, Charset.forName("utf-8")));
    }

    static class InjectableTemplate implements Template {

        private final String path;
        private final Supplier<Engine> engine;

        public InjectableTemplate(Supplier<Engine> engine, String path) {
            this.engine = engine;
            this.path = path;
        }

        @Override
        public String render(Object data) {
            return delegate().render(data);
        }

        @Override
        public CompletionStage<Void> render(Object data, Consumer<String> resultConsumer) {
            return delegate().render(data, resultConsumer);
        }

        Template delegate() {
            return engine.get().getTemplate(path);
        }

    }

}
