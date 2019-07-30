package com.github.mkouba.qute.quarkus.resteasy.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mkouba.qute.quarkus.resteasy.QuteResteasyRecorder;
import com.github.mkouba.qute.quarkus.resteasy.TemplateResponseFilter;
import com.github.mkouba.qute.quarkus.resteasy.TemplateVariantProducer;
import com.github.mkouba.qute.quarkus.resteasy.Variant;
import com.github.mkouba.qute.quarkus.runtime.QuteConfig;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.resteasy.common.deployment.ResteasyJaxrsProviderBuildItem;

public class QuteResteasyProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuteResteasyProcessor.class);

    private static final DotName VARIANT = DotName.createSimple(Variant.class.getName());

    @BuildStep
    AdditionalBeanBuildItem additionalBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(TemplateVariantProducer.class, Variant.class).build();
    }

    @BuildStep
    ResteasyJaxrsProviderBuildItem registerProviders() {
        return new ResteasyJaxrsProviderBuildItem(TemplateResponseFilter.class.getName());
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    public void collectTemplateVariants(QuteResteasyRecorder recorder, QuteConfig config, BeanContainerBuildItem beanContainer,
            ApplicationArchivesBuildItem applicationArchivesBuildItem,
            BeanArchiveIndexBuildItem beanArchiveIndex) throws IOException {

        IndexView index = beanArchiveIndex.getIndex();
        ApplicationArchive applicationArchive = applicationArchivesBuildItem.getRootArchive();
        String basePath = "META-INF/resources/" + config.basePath + "/";
        Path templatesPath = applicationArchive.getChildPath(basePath);

        if (templatesPath == null) {
            return;
        }

        Set<String> variantBases = new HashSet<>();

        // TODO analyze injection points instead
        for (AnnotationInstance variant : index.getAnnotations(VARIANT)) {

            AnnotationValue value = variant.value();
            LOGGER.debug("Found {} declared on {}", variant, variant.target());

            if (value == null || value.asString().isEmpty()) {
                if (variant.target().kind() == AnnotationTarget.Kind.FIELD) {
                    variantBases.add(basePath + variant.target().asField().name());
                }
                // TODO method params
            } else {
                // E.g. "items", "detail/item2"
                variantBases.add(basePath + value.asString());
            }
        }

        Map<String, List<String>> variants = new HashMap<>();
        scanVariants(basePath, templatesPath, templatesPath, variantBases, variants);
        
        LOGGER.info("Variants found: {}", variants);

        recorder.start(beanContainer.getValue(), variants);
    }

    void scanVariants(String basePath, Path root, Path directory, Set<String> variantBases, Map<String, List<String>> variants)
            throws IOException {
        Iterator<Path> files = Files.list(directory)
                .iterator();
        while (files.hasNext()) {
            Path path = files.next();
            if (Files.isRegularFile(path)) {
                for (String base : variantBases) {
                    if (path.toAbsolutePath().toString().contains(base)) {
                        // Variants are relative paths to base, e.g. "detail/item2"
                        variants.computeIfAbsent(base.substring(basePath.length()), i -> new ArrayList<>())
                                .add(root.relativize(path).toString());
                    }
                }
            } else if (Files.isDirectory(path)) {
                scanVariants(basePath, root, path, variantBases, variants);
            }
        }
    }

}
