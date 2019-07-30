package com.github.mkouba.qute.quarkus.runtime;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mkouba.qute.Engine;
import com.github.mkouba.qute.EngineBuilder;
import com.github.mkouba.qute.NamespaceResolver;
import com.github.mkouba.qute.Results.Result;
import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.UserTagSectionHelper;
import com.github.mkouba.qute.ValueResolver;
import com.github.mkouba.qute.quarkus.ResourcePath;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

@Singleton
public class TemplateProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateProducer.class);

    @Inject
    Event<EngineBuilder> event;

    private Engine engine;
    private List<String> tags;

    private List<String> suffixes;
    private String basePath;
    private String tagPath;
    
    void init(QuteConfig config, List<String> resolverClasses, List<String> templatePaths, List<String> tags) {
        if (engine != null) {
            LOGGER.warn("Qute already initialized!");
            return;
        }
        LOGGER.debug("Initializing Qute with: {}", resolverClasses);
        
        suffixes = config.suffixes;
        basePath = "META-INF/resources/" + config.basePath + "/";
        tagPath = basePath.endsWith("/") ? basePath + "tags/" : basePath + "/tags/";
        
        EngineBuilder builder = Engine.builder()
                .addDefaultSectionHelpers().addDefaultValueResolvers();
        // Allow anyone to customize the builder
        event.fire(builder);
        // Resolve @Named beans
        builder.addNamespaceResolver(NamespaceResolver.builder("inject").resolve(ctx -> {
            InstanceHandle<Object> bean = Arc.container().instance(ctx.getName());
            return bean.isAvailable() ? bean.get() : Result.NOT_FOUND;
        }).build());
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
    Template getDefaultTemplate(InjectionPoint injectionPoint) {
        String name = null;
        if (injectionPoint.getMember() instanceof Field) {
            // For "@Inject Template items" use "items"
            name = injectionPoint.getMember().getName();
        } else {
            AnnotatedParameter<?> parameter = (AnnotatedParameter<?>) injectionPoint.getAnnotated();
            if (parameter.getJavaParameter().isNamePresent()) {
                name = parameter.getJavaParameter().getName();
            } else {
                name = injectionPoint.getMember().getName();
                LOGGER.warn("Parameter name not present - using the method name as the template name instead {}", name);
            }
        }
        // Note that engine may not be initialized and so we inject a delegating template
        return new InjectableTemplate(this::getEngine, name, suffixes);
    }

    @Produces
    @ResourcePath("ignored")
    Template getTemplate(InjectionPoint injectionPoint) {
        ResourcePath path = null;
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(ResourcePath.class)) {
                path = (ResourcePath) qualifier;
                break;
            }
        }
        if (path == null || path.value().isEmpty()) {
            throw new IllegalStateException("No template reource path specified");
        }
        // Note that engine may not be initialized and so we inject a delegating template
        return new InjectableTemplate(this::getEngine, path.value(), suffixes);
    }

    @Produces
    @ApplicationScoped
    Engine getEngine() {
        return engine;
    }

    String getBasePath() {
        return basePath;
    }

    String getTagPath() {
        return tagPath;
    }

    /**
     * @param path
     * @return the optional reader
     */
    private Optional<Reader> locate(String path) {
        InputStream in = null;
        // First try to locate a tag template
        if (tags.stream().anyMatch(tag -> tag.startsWith(path))) {
            LOGGER.debug("Locate tag for {}", path);
            in = locatePath(tagPath + path);
            // Try path with suffixes
            for (String suffix : suffixes) {
                in = locatePath(tagPath + path + "." + suffix);
                if (in != null) {
                    break;
                }
            }
        }
        if (in == null) {
            String templatePath = basePath + path;
            LOGGER.debug("Locate template for {}", templatePath);
            in = locatePath(templatePath);
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

        private final Supplier<Template> template;

        public InjectableTemplate(Supplier<Engine> engineSupplier, String path, Iterable<String> suffixes) {
            this.template = () -> {
                Engine engine = engineSupplier.get();
                Template template = engine.getTemplate(path);
                if (template == null) {
                    // Try path with suffixes
                    for (String suffix : suffixes) {
                        template = engine.getTemplate(path + "." + suffix);
                        if (template != null) {
                            break;
                        }
                    }
                    if (template == null) {
                        throw new IllegalStateException("No template found for path: " + path);
                    }
                }
                return template;
            };
        }

        @Override
        public Rendering render() {
            return template.get().render();
        }

    }

}
