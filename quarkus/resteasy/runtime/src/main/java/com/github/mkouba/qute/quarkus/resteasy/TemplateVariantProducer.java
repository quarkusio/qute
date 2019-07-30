package com.github.mkouba.qute.quarkus.resteasy;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Variant;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mkouba.qute.Engine;
import com.github.mkouba.qute.RenderingBase;
import com.github.mkouba.qute.Template;

@Singleton
public class TemplateVariantProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateVariantProducer.class);

    @Inject
    Instance<Engine> engine;

    private Map<String, TemplateVariants> templateVariants;

    void init(Map<String, List<String>> variants) {
        if (templateVariants != null) {
            LOGGER.warn("Qute RESTEasy already initialized!");
            return;
        }
        LOGGER.debug("Initializing Qute RESTEasy with: {}", templateVariants);

        templateVariants = new HashMap<>();
        for (Entry<String, List<String>> entry : variants.entrySet()) {
            TemplateVariants var = new TemplateVariants(initVariants(entry.getKey(), entry.getValue()), entry.getKey());
            templateVariants.put(entry.getKey(), var);
        }
    }

    @Produces
    @com.github.mkouba.qute.quarkus.resteasy.Variant
    Template getVariant(InjectionPoint injectionPoint) {
        com.github.mkouba.qute.quarkus.resteasy.Variant variant = null;
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(com.github.mkouba.qute.quarkus.resteasy.Variant.class)) {
                variant = (com.github.mkouba.qute.quarkus.resteasy.Variant) qualifier;
                break;
            }
        }
        if (variant == null) {
            throw new IllegalStateException("No @Variant found");
        }
        // Note that engine may not be initialized and so we inject a delegating template
        if (variant.value().isEmpty()) {
            // For "@Variant Template items" use "items"
            return new VariantTemplate(injectionPoint.getMember().getName());
        } else {
            return new VariantTemplate(variant.value());
        }
    }

    class VariantTemplate implements Template {

        private final String baseName;

        VariantTemplate(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Rendering render() {
            return new VariantRenderingImpl(engine.get(), baseName);
        }

    }

    class VariantRenderingImpl extends RenderingBase implements VariantRendering {

        private final String baseName;
        private final Engine engine;
        private String selectedVariant;

        VariantRenderingImpl(Engine engine, String baseName) {
            this.engine = engine;
            this.baseName = baseName;
        }
        
        public String getBaseName() {
            return baseName;
        }
        
        public String getSelectedVariant() {
            return selectedVariant;
        }

        @Override
        public String asString() {
            return template().render().setData(data()).asString();
        }

        @Override
        public Publisher<String> publisher() {
            return template().render().setData(data()).publisher();
        }

        @Override
        public CompletionStage<Void> consume(Consumer<String> consumer) {
            return template().render().setData(data()).consume(consumer);
        }

        @Override
        public void selectVariant(Request request) {
            List<javax.ws.rs.core.Variant> variants = new ArrayList<>(templateVariants.get(baseName).variants.keySet());
            javax.ws.rs.core.Variant variant = request.selectVariant(variants);
            if (variant != null) {
                selectedVariant = templateVariants.get(baseName).variants.get(variant);
            } else {
                selectedVariant = templateVariants.get(baseName).defaultTemplate;
            }
        }

        private Template template() {
            return engine.getTemplate(selectedVariant);
        }

    }

    static class TemplateVariants {

        public final Map<Variant, String> variants;
        public final String defaultTemplate;

        public TemplateVariants(Map<Variant, String> variants, String defaultTemplate) {
            this.variants = variants;
            this.defaultTemplate = defaultTemplate;
        }

    }

    static MediaType parseMediaType(String suffix) {
        if (suffix.equalsIgnoreCase(".html")) {
            return MediaType.TEXT_HTML_TYPE;
        } else if (suffix.equalsIgnoreCase(".xml")) {
            return MediaType.APPLICATION_XML_TYPE;
        } else if (suffix.equalsIgnoreCase(".txt")) {
            return MediaType.TEXT_PLAIN_TYPE;
        } else if (suffix.equalsIgnoreCase(".json")) {
            return MediaType.APPLICATION_JSON_TYPE;
        }
        LOGGER.warn("Unknown media type for suffix: " + suffix);
        return MediaType.APPLICATION_OCTET_STREAM_TYPE;
    }

    static MediaType parseMediaType(String base, String variant) {
        String suffix = variant.substring(base.length());
        return parseMediaType(suffix);
    }

    private static Map<Variant, String> initVariants(String base, List<String> availableVariants) {
        Map<Variant, String> map = new HashMap<>();
        for (String path : availableVariants) {
            if (!base.equals(path)) {
                MediaType mediaType = parseMediaType(base, path);
                map.put(new Variant(mediaType, (String) null, null), path);
            }
        }
        return map;
    }

}
