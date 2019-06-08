package com.github.mkouba.qute.quarkus.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.generator.ExtensionMethodGenerator;
import com.github.mkouba.qute.generator.ValueResolverGenerator;
import com.github.mkouba.qute.quarkus.TemplatePath;
import com.github.mkouba.qute.quarkus.runtime.QuteTemplate;
import com.github.mkouba.qute.quarkus.runtime.TemplateProducer;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.gizmo.ClassOutput;

public class QuteProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuteProcessor.class);

    static final DotName TEMPLATE_PATH = DotName.createSimple(TemplatePath.class.getName());
    
    @BuildStep
    void generateValueResolvers(BuildProducer<GeneratedClassBuildItem> generatedClass,
            BeanArchiveIndexBuildItem beanArchiveIndex, ApplicationArchivesBuildItem applicationArchivesBuildItem,
            BuildProducer<GeneratedValueResolverBuildItem> generatedResolvers) {

        IndexView index = beanArchiveIndex.getIndex();
        Predicate<String> appClassPredicate = new Predicate<String>() {
            @Override
            public boolean test(String name) {
                if (applicationArchivesBuildItem.getRootArchive().getIndex()
                        .getClassByName(DotName.createSimple(name)) != null) {
                    return true;
                }
                // TODO generated classes?
                return false;
            }
        };
        ClassOutput classOutput = new ClassOutput() {
            @Override
            public void write(String name, byte[] data) {
                int idx = name.lastIndexOf(ExtensionMethodGenerator.SUFFIX);
                if (idx == -1) {
                    idx = name.lastIndexOf(ValueResolverGenerator.SUFFIX);
                }
                String className = name.substring(0, idx).replace("/", ".");
                boolean appClass = appClassPredicate.test(className);
                LOGGER.debug("Writing {} [appClass={}]", name, appClass);
                generatedClass.produce(new GeneratedClassBuildItem(appClass, name, data));
            }
        };

        Set<ClassInfo> controlled = new HashSet<>();
        Map<ClassInfo, AnnotationInstance> uncontrolled = new HashMap<>();
        for (AnnotationInstance templateData : index.getAnnotations(ValueResolverGenerator.TEMPLATE_DATA)) {
            processsTemplateData(index, templateData, templateData.target(), controlled, uncontrolled);
        }
        for (AnnotationInstance containerInstance : index.getAnnotations(ValueResolverGenerator.TEMPLATE_DATA_CONTAINER)) {
            for (AnnotationInstance templateData : containerInstance.value().asNestedArray()) {
                processsTemplateData(index, templateData, containerInstance.target(), controlled, uncontrolled);
            }
        }

        ValueResolverGenerator generator = ValueResolverGenerator.builder().setIndex(index).setClassOutput(classOutput)
                .setUncontrolled(uncontrolled)
                .build();

        // @TemplateData
        for (ClassInfo data : controlled) {
            generator.generate(data);
        }
        // Uncontrolled classes
        for (ClassInfo data : uncontrolled.keySet()) {
            generator.generate(data);
        }
        // @Named beans
        for (AnnotationInstance templateData : index.getAnnotations(DotNames.NAMED)) {
            generator.generate(templateData.target().asClass());
        }

        Set<String> generateTypes = new HashSet<>();
        generateTypes.addAll(generator.getGeneratedTypes());

        ExtensionMethodGenerator extensionMethodGenerator = new ExtensionMethodGenerator(classOutput);
        for (AnnotationInstance templateExtension : index.getAnnotations(ExtensionMethodGenerator.TEMPLATE_EXTENSION)) {
            if (templateExtension.target().kind() == Kind.METHOD) {
                MethodInfo method = templateExtension.target().asMethod();
                extensionMethodGenerator.generate(method);
            }
        }
        generateTypes.addAll(extensionMethodGenerator.getGeneratedTypes());

        for (String generateType : generateTypes) {
            generatedResolvers.produce(new GeneratedValueResolverBuildItem(generateType.replace("/", ".")));
        }
    }

    void processsTemplateData(IndexView index, AnnotationInstance templateData, AnnotationTarget annotationTarget,
            Set<ClassInfo> controlled, Map<ClassInfo, AnnotationInstance> uncontrolled) {
        AnnotationValue targetValue = templateData.value("target");
        if (targetValue == null || targetValue.asClass().name().equals(ValueResolverGenerator.TEMPLATE_DATA)) {
            controlled.add(annotationTarget.asClass());
        } else {
            ClassInfo uncontrolledClass = index.getClassByName(targetValue.asClass().name());
            if (uncontrolledClass != null) {
                uncontrolled.compute(uncontrolledClass, (c, v) -> {
                    if (v == null) {
                        return templateData;
                    }
                    // Merge annotation values
                    AnnotationValue ignoreValue = templateData.value("ignore");
                    if (ignoreValue == null || !ignoreValue.equals(v.value("ignore"))) {
                        ignoreValue = AnnotationValue.createArrayValue("ignore", new AnnotationValue[] {});
                    }
                    AnnotationValue propertiesValue = templateData.value("properties");
                    if (propertiesValue == null || propertiesValue.equals(v.value("properteis"))) {
                        propertiesValue = AnnotationValue.createBooleanValue("properties", false);
                    }
                    return AnnotationInstance.create(templateData.name(), templateData.target(),
                            new AnnotationValue[] { ignoreValue, propertiesValue });
                });
            } else {
                LOGGER.warn("@TemplateData#target() not available: {}", annotationTarget.asClass().name());
            }
        }
    }

    @BuildStep
    void collectTemplatePaths(BeanArchiveIndexBuildItem beanArchiveIndex,
            BuildProducer<HotDeploymentWatchedFileBuildItem> hotDeploymentFiles, BuildProducer<TemplatePathBuildItem> paths) {
        Set<String> templatePaths = new HashSet<>();
        for (AnnotationInstance templatePath : beanArchiveIndex.getIndex().getAnnotations(TEMPLATE_PATH)) {
            AnnotationValue pathValue = templatePath.value();
            if (pathValue != null && !pathValue.asString().isEmpty()) {
                templatePaths.add(pathValue.asString());
            }
        }
        for (String path : templatePaths) {
            if (path.isEmpty()) {
                continue;
            }
            paths.produce(new TemplatePathBuildItem(path));
            hotDeploymentFiles.produce(new HotDeploymentWatchedFileBuildItem("META-INF/resources/" + path));
        }
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    void initialize(QuteTemplate template,
            List<GeneratedValueResolverBuildItem> generatedValueResolvers, List<TemplatePathBuildItem> templatePaths,
            BeanContainerBuildItem beanContainer,
            List<ServiceStartBuildItem> startedServices) {
        template.start(beanContainer.getValue(), generatedValueResolvers.stream()
                .map(GeneratedValueResolverBuildItem::getClassName).collect(Collectors.toList()),
                templatePaths.stream().map(TemplatePathBuildItem::getPath).collect(Collectors.toList()));
    }

    @BuildStep
    void additionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans
                .produce(AdditionalBeanBuildItem.builder()
                        .addBeanClasses(TemplateProducer.class, TemplatePath.class, Template.class).build());
    }

}
