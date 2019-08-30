package io.quarkus.qute.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.function.Supplier;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.qute.Template;
import io.quarkus.qute.api.ResourcePath;

@Singleton
public class TemplateProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateProducer.class);

    @Inject
    EngineProducer engineProducer;

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
        return new InjectableTemplate(name, engineProducer.getSuffixes());
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
        return new InjectableTemplate(path.value(), engineProducer.getSuffixes());
    }

    class InjectableTemplate implements Template {

        private final Supplier<Template> template;

        public InjectableTemplate(String path, Iterable<String> suffixes) {
            this.template = () -> {
                Template template = engineProducer.getEngine().getTemplate(path);
                if (template == null) {
                    // Try path with suffixes
                    for (String suffix : suffixes) {
                        template = engineProducer.getEngine().getTemplate(path + "." + suffix);
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