package io.quarkus.qute.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.substrate.ServiceProviderBuildItem;
import io.quarkus.deployment.builditem.substrate.SubstrateResourceBuildItem;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.qute.PublisherFactory;
import io.quarkus.qute.Template;
import io.quarkus.qute.api.ResourcePath;
import io.quarkus.qute.api.VariantTemplate;
import io.quarkus.qute.generator.ExtensionMethodGenerator;
import io.quarkus.qute.generator.ValueResolverGenerator;
import io.quarkus.qute.runtime.EngineProducer;
import io.quarkus.qute.runtime.QuteConfig;
import io.quarkus.qute.runtime.QuteRecorder;
import io.quarkus.qute.runtime.TemplateProducer;
import io.quarkus.qute.runtime.VariantTemplateProducer;
import io.quarkus.qute.rxjava.RxjavaPublisherFactory;

public class QuteProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuteProcessor.class);

    public static final DotName RESOURCE_PATH = DotName.createSimple(ResourcePath.class.getName());
    public static final DotName TEMPLATE = DotName.createSimple(Template.class.getName());
    public static final DotName VARIANT_TEMPLATE = DotName.createSimple(VariantTemplate.class.getName());

    @BuildStep
    AdditionalBeanBuildItem additionalBeans() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(EngineProducer.class, TemplateProducer.class, VariantTemplateProducer.class, ResourcePath.class,
                        Template.class, Template.Rendering.class)
                .build();
    }

    @BuildStep
    void generateValueResolvers(BuildProducer<GeneratedClassBuildItem> generatedClass,
            BeanArchiveIndexBuildItem beanArchiveIndex, ApplicationArchivesBuildItem applicationArchivesBuildItem,
            BuildProducer<GeneratedValueResolverBuildItem> generatedResolvers,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {

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

        LOGGER.debug("Generated types: {}", generateTypes);

        for (String generateType : generateTypes) {
            generatedResolvers.produce(new GeneratedValueResolverBuildItem(generateType));
            reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, generateType));
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
                    if (propertiesValue == null || propertiesValue.equals(v.value("properties"))) {
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
    void collectTemplates(QuteConfig config, ApplicationArchivesBuildItem applicationArchivesBuildItem,
            BeanArchiveIndexBuildItem beanArchiveIndex,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedPaths,
            BuildProducer<TemplatePathBuildItem> templatePaths,
            BuildProducer<SubstrateResourceBuildItem> substrateResources)
            throws IOException {
        ApplicationArchive applicationArchive = applicationArchivesBuildItem.getRootArchive();
        String basePath = "META-INF/resources/" + config.basePath + "/";
        Path templatesPath = applicationArchive.getChildPath(basePath);

        if (templatesPath != null) {
            scan(templatesPath, templatesPath, basePath, watchedPaths, templatePaths, substrateResources);
        }

        String tagBasePath = basePath + "tags/";
        Path tagsPath = applicationArchive.getChildPath(tagBasePath);
        if (tagsPath != null) {
            Iterator<Path> tagFiles = Files.list(tagsPath)
                    .filter(Files::isRegularFile)
                    .iterator();
            while (tagFiles.hasNext()) {
                Path path = tagFiles.next();
                String tagPath = path.getFileName().toString();
                LOGGER.debug("Found tag: {}", path);
                produceTemplateBuildItems(templatePaths, watchedPaths, substrateResources, tagBasePath, tagPath, true);
            }
        }
    }

    @BuildStep
    void processInjectionPoints(QuteConfig config, ApplicationArchivesBuildItem applicationArchivesBuildItem,
            List<TemplatePathBuildItem> templatePaths, ValidationPhaseBuildItem validationPhase,
            BuildProducer<ValidationErrorBuildItem> validationErrors, BuildProducer<TemplateVariantsBuildItem> templateVariants)
            throws IOException {

        ApplicationArchive applicationArchive = applicationArchivesBuildItem.getRootArchive();
        String basePath = "META-INF/resources/" + config.basePath + "/";
        Path templatesPath = applicationArchive.getChildPath(basePath);

        // Remove suffix from the path; e.g. "items.html" becomes "items"
        Set<String> filePaths = new HashSet<String>();
        for (TemplatePathBuildItem templatePath : templatePaths) {
            filePaths.add(templatePath.getPath());
            int idx = templatePath.getPath().lastIndexOf('.');
            if (idx != -1) {
                filePaths.add(templatePath.getPath().substring(0, idx));
            }
        }

        Set<String> variantBases = new HashSet<>();

        for (InjectionPointInfo injectionPoint : validationPhase.getContext().get(BuildExtension.Key.INJECTION_POINTS)) {

            if (injectionPoint.getRequiredType().name().equals(TEMPLATE)) {

                AnnotationInstance resourcePath = injectionPoint.getRequiredQualifier(RESOURCE_PATH);
                String name;
                if (resourcePath != null) {
                    name = resourcePath.value().asString();
                } else if (injectionPoint.hasDefaultedQualifier()) {
                    name = getName(injectionPoint);
                } else {
                    name = null;
                }
                if (name != null) {
                    // For "@Inject Template items" we try to match "items"
                    // For "@ResourcePath("github/pulls") Template pulls" we try to match "github/pulls"
                    if (filePaths.stream().noneMatch(path -> path.endsWith(name))) {
                        validationErrors.produce(new ValidationErrorBuildItem(
                                new IllegalStateException("No template found for " + injectionPoint.getTargetInfo())));
                    }
                }

            } else if (injectionPoint.getRequiredType().name().equals(VARIANT_TEMPLATE)) {

                AnnotationInstance resourcePath = injectionPoint.getRequiredQualifier(RESOURCE_PATH);
                String name;
                if (resourcePath != null) {
                    name = resourcePath.value().asString();
                } else if (injectionPoint.hasDefaultedQualifier()) {
                    name = getName(injectionPoint);
                } else {
                    name = null;
                }
                if (name != null) {
                    if (filePaths.stream().noneMatch(path -> path.endsWith(name))) {
                        validationErrors.produce(new ValidationErrorBuildItem(
                                new IllegalStateException("No variant template found for " + injectionPoint.getTargetInfo())));
                    } else {
                        variantBases.add(name);
                    }
                }
            }
        }

        if (!variantBases.isEmpty()) {
            Map<String, List<String>> variants = new HashMap<>();
            scanVariants(basePath, templatesPath, templatesPath, variantBases, variants);
            templateVariants.produce(new TemplateVariantsBuildItem(variants));
            LOGGER.debug("Variant templates found: {}", variants);
        }
    }

    @BuildStep
    ServiceProviderBuildItem registerPublisherFactory() {
        return new ServiceProviderBuildItem(PublisherFactory.class.getName(), RxjavaPublisherFactory.class.getName());
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    void initialize(QuteRecorder recorder, QuteConfig config,
            List<GeneratedValueResolverBuildItem> generatedValueResolvers, List<TemplatePathBuildItem> templatePaths,
            Optional<TemplateVariantsBuildItem> templateVariants,
            BeanContainerBuildItem beanContainer,
            List<ServiceStartBuildItem> startedServices) {

        List<String> templates = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (TemplatePathBuildItem templatePath : templatePaths) {
            if (templatePath.isTag()) {
                tags.add(templatePath.getPath());
            } else {
                templates.add(templatePath.getPath());
            }
        }

        recorder.initEngine(config, beanContainer.getValue(), generatedValueResolvers.stream()
                .map(GeneratedValueResolverBuildItem::getClassName).collect(Collectors.toList()),
                templates,
                tags);

        Map<String, List<String>> variants;
        if (templateVariants.isPresent()) {
            variants = templateVariants.get().getVariants();
        } else {
            variants = Collections.emptyMap();
        }
        recorder.initVariants(beanContainer.getValue(), variants);
    }

    private String getName(InjectionPointInfo injectionPoint) {
        if (injectionPoint.isField()) {
            return injectionPoint.getTarget().asField().name();
        } else if (injectionPoint.isParam()) {
            String name = injectionPoint.getTarget().asMethod().parameterName(injectionPoint.getPosition());
            return name == null ? injectionPoint.getTarget().asMethod().name() : name;
        }
        throw new IllegalArgumentException();
    }

    private static void produceTemplateBuildItems(BuildProducer<TemplatePathBuildItem> templatePaths,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedPaths,
            BuildProducer<SubstrateResourceBuildItem> substrateResources, String basePath, String filePath, boolean tag) {
        if (filePath.isEmpty()) {
            return;
        }
        String fullPath = basePath + filePath;
        watchedPaths.produce(new HotDeploymentWatchedFileBuildItem(fullPath, false));
        substrateResources.produce(new SubstrateResourceBuildItem(fullPath));
        templatePaths.produce(new TemplatePathBuildItem(filePath, tag));
    }

    private void scan(Path root, Path directory, String basePath, BuildProducer<HotDeploymentWatchedFileBuildItem> watchedPaths,
            BuildProducer<TemplatePathBuildItem> templatePaths, BuildProducer<SubstrateResourceBuildItem> substrateResources)
            throws IOException {
        Iterator<Path> files = Files.list(directory).iterator();
        while (files.hasNext()) {
            Path path = files.next();
            if (Files.isRegularFile(path)) {
                LOGGER.debug("Found template: {}", path);
                String templatePath = root.relativize(path).toString();
                produceTemplateBuildItems(templatePaths, watchedPaths, substrateResources, basePath, templatePath, false);
            } else if (Files.isDirectory(path) && !path.getFileName().toString().equals("tags")) {
                LOGGER.debug("Scan directory: {}", path);
                scan(root, path, basePath, watchedPaths, templatePaths, substrateResources);
            }
        }
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
                        variants.computeIfAbsent(base, i -> new ArrayList<>())
                                .add(root.relativize(path).toString());
                    }
                }
            } else if (Files.isDirectory(path)) {
                scanVariants(basePath, root, path, variantBases, variants);
            }
        }
    }

}
