package com.github.mkouba.qute.quarkus.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.Typed;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mkouba.qute.Engine;
import com.github.mkouba.qute.RenderingBase;
import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.quarkus.ResourcePath;
import com.github.mkouba.qute.quarkus.Variant;
import com.github.mkouba.qute.quarkus.VariantTemplate;

@Singleton
public class VariantTemplateProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(VariantTemplateProducer.class);

    @Inject
    Instance<Engine> engine;

    private Map<String, TemplateVariants> templateVariants;

    void init(Map<String, List<String>> variants) {
        if (templateVariants != null) {
            LOGGER.warn("Qute VariantTemplateProducer already initialized!");
            return;
        }
        LOGGER.debug("Initializing VariantTemplateProducer: {}", templateVariants);

        templateVariants = new HashMap<>();
        for (Entry<String, List<String>> entry : variants.entrySet()) {
            TemplateVariants var = new TemplateVariants(initVariants(entry.getKey(), entry.getValue()), entry.getKey());
            templateVariants.put(entry.getKey(), var);
        }
    }

    @Typed(VariantTemplate.class)
    @Produces
    VariantTemplate getDefaultVariantTemplate(InjectionPoint injectionPoint) {
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
        return new VariantTemplateImpl(name);
    }

    @Typed(VariantTemplate.class)
    @Produces
    @ResourcePath("ignored")
    VariantTemplate getVariantTemplate(InjectionPoint injectionPoint) {
        ResourcePath path = null;
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(ResourcePath.class)) {
                path = (ResourcePath) qualifier;
                break;
            }
        }
        if (path == null || path.value().isEmpty()) {
            throw new IllegalStateException("No variant template reource path specified");
        }
        return new VariantTemplateImpl(path.value());
    }

    class VariantTemplateImpl implements VariantTemplate {

        private final String baseName;

        VariantTemplateImpl(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Rendering render() {
            return new VariantRenderingImpl(templateVariants.get(baseName));
        }

    }

    class VariantRenderingImpl extends RenderingBase {

        private final TemplateVariants variants;

        VariantRenderingImpl(TemplateVariants variants) {
            this.variants = variants;
            setAttribute(VariantTemplate.VARIANTS, new ArrayList<>(variants.variantToTemplate.keySet()));
        }

        @Override
        public String getResult() {
            return template().render().setData(data()).getResult();
        }

        @Override
        public CompletionStage<String> getResultAsync() {
            return template().render().setData(data()).getResultAsync();
        }

        @Override
        public Publisher<String> publisher() {
            return template().render().setData(data()).publisher();
        }

        @Override
        public CompletionStage<Void> consume(Consumer<String> consumer) {
            return template().render().setData(data()).consume(consumer);
        }

        private Template template() {
            Variant selected = (Variant) getAttribute(VariantTemplate.SELECTED_VARIANT);
            String name = selected != null ? variants.variantToTemplate.get(selected) : variants.defaultTemplate;
            return engine.get().getTemplate(name);
        }

    }

    static class TemplateVariants {

        public final Map<Variant, String> variantToTemplate;
        public final String defaultTemplate;

        public TemplateVariants(Map<Variant, String> variants, String defaultTemplate) {
            this.variantToTemplate = variants;
            this.defaultTemplate = defaultTemplate;
        }

    }

    static String parseMediaType(String suffix) {
        // TODO support more media types...
        if (suffix.equalsIgnoreCase(".html")) {
            return "text/html";
        } else if (suffix.equalsIgnoreCase(".xml")) {
            return "text/xml";
        } else if (suffix.equalsIgnoreCase(".txt")) {
            return "text/plain";
        } else if (suffix.equalsIgnoreCase(".json")) {
            return "application/json";
        }
        LOGGER.warn("Unknown media type for suffix: " + suffix);
        return "application/octet-stream";
    }

    static String parseMediaType(String base, String variant) {
        String suffix = variant.substring(base.length());
        return parseMediaType(suffix);
    }

    private static Map<Variant, String> initVariants(String base, List<String> availableVariants) {
        Map<Variant, String> map = new HashMap<>();
        for (String path : availableVariants) {
            if (!base.equals(path)) {
                String mediaType = parseMediaType(base, path);
                map.put(new Variant(null, mediaType, null), path);
            }
        }
        return map;
    }

}
