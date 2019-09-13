package io.quarkus.qute.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDeploymentValidatorBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BeanDeploymentValidator;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.substrate.ServiceProviderBuildItem;
import io.quarkus.deployment.builditem.substrate.SubstrateResourceBuildItem;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Expression;
import io.quarkus.qute.PublisherFactory;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.Template;
import io.quarkus.qute.api.ResourcePath;
import io.quarkus.qute.api.VariantTemplate;
import io.quarkus.qute.deployment.TemplatesAnalysisBuildItem.TemplateAnalysis;
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
    TemplatesAnalysisBuildItem analyzeTemplates(List<TemplatePathBuildItem> templatePaths) {
        long start = System.currentTimeMillis();
        List<TemplateAnalysis> analysis = new ArrayList<>();

        Engine dummyEngine = Engine.builder().addDefaultSectionHelpers().computeSectionHelper(name -> {
            return new SectionHelperFactory<SectionHelper>() {
                @Override
                public SectionHelper initialize(SectionInitContext context) {
                    return new SectionHelper() {
                        @Override
                        public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
                            return CompletableFuture.completedFuture(ResultNode.NOOP);
                        }
                    };
                }
            };
        }).build();

        for (TemplatePathBuildItem path : templatePaths) {
            try {
                Template template = dummyEngine.parse(new String(Files.readAllBytes(path.getFullPath())));
                analysis.add(new TemplateAnalysis(template.getExpressions(), path.getFullPath()));
            } catch (IOException e) {
                LOGGER.warn("Unable to analyze the template from path: " + path.getFullPath(), e);
            }
        }
        LOGGER.debug("Finished analysis of {} templates  in {} ms",
                analysis.size(), System.currentTimeMillis() - start);
        return new TemplatesAnalysisBuildItem(analysis);
    }

    @BuildStep
    void validateInjectedBeans(QuteConfig config, ApplicationArchivesBuildItem applicationArchivesBuildItem,
            TemplatesAnalysisBuildItem analysis, BeanArchiveIndexBuildItem beanArchiveIndex,
            BuildProducer<BeanDeploymentValidatorBuildItem> validator) {
        ApplicationArchive applicationArchive = applicationArchivesBuildItem.getRootArchive();
        Set<Expression> injectExpressions = collectInjectExpressions(analysis);
        Set<String> expressionNames = injectExpressions.stream().map(ex -> ex.parts.get(0)).collect(Collectors.toSet());

        if (!injectExpressions.isEmpty()) {
            validator.produce(new BeanDeploymentValidatorBuildItem(new BeanDeploymentValidator() {

                @Override
                public void validate(ValidationContext context) {

                    List<BeanInfo> beans = context.get(BuildExtension.Key.BEANS);
                    Set<String> beanNames = beans.stream().map(BeanInfo::getName)
                            .filter(Objects::nonNull).collect(Collectors.toSet());
                    expressionNames.removeAll(beanNames);
                    if (!expressionNames.isEmpty()) {
                        // Injecting non-existing bean
                        Map<String, List<String>> expressionToTemplate = new HashMap<>();
                        for (String name : expressionNames) {
                            List<String> templates = new ArrayList<>();
                            for (TemplateAnalysis template : analysis.getAnalysis()) {
                                if (collectInjectExpressions(analysis).stream().anyMatch(ex -> name.equals(ex.parts.get(0)))) {
                                    templates.add(applicationArchive.getArchiveRoot().relativize(template.path).toString());
                                }
                            }
                            expressionToTemplate.put(name, templates);
                        }
                        context.addDeploymentProblem(new IllegalStateException(
                                "An inject: expression is referencing non-existing @Named bean: \n"
                                        + expressionToTemplate.entrySet().stream()
                                                .map(e -> "- " + e.getKey() + "="
                                                        + e.getValue().stream().collect(Collectors.joining(",")))
                                                .collect(Collectors.joining("\n"))));
                    }

                    // Validate properties
                    for (TemplateAnalysis template : analysis.getAnalysis()) {
                        for (Expression expression : collectInjectExpressions(template)) {
                            if (expression.parts.size() > 1) {
                                String name = expression.parts.get(0);
                                BeanInfo bean = beans.stream()
                                        .filter(b -> name.equals(b.getName())).findAny().orElse(null);
                                if (bean != null && !beanHasProperty(name, expression.parts.get(1), bean, beanArchiveIndex.getIndex())) {
                                    context.addDeploymentProblem(new IllegalStateException(
                                            "Property " + expression.parts.get(1) + " not found on a @Named bean "
                                                    + bean.toString()));
                                }
                            }
                        }
                    }
                }
            }));
        }
    }

    private boolean beanHasProperty(String name, String property, BeanInfo bean, IndexView index) {
        ClassInfo beanClass = bean.getImplClazz();
        while (beanClass != null) {
            // Fields
            for (FieldInfo field : beanClass.fields()) {
                if (Modifier.isPublic(field.flags()) && field.name().equals(property)) {
                    return true;
                }
            }
            // Methods
            for (MethodInfo method : beanClass.methods()) {
                if (Modifier.isPublic(method.flags()) && (method.name().equals(property)
                        || ValueResolverGenerator.getPropertyName(method.name()).equals(property))) {
                    return true;
                }
            }

            if (beanClass.superName().equals(DotNames.OBJECT)) {
                beanClass = null;
            } else {
                beanClass = index.getClassByName(beanClass.superName());
            }
        }
        return false;
    }

    @BuildStep
    void generateValueResolvers(QuteConfig config, BuildProducer<GeneratedClassBuildItem> generatedClass,
            CombinedIndexBuildItem combinedIndex, BeanArchiveIndexBuildItem beanArchiveIndex,
            ApplicationArchivesBuildItem applicationArchivesBuildItem,
            List<TemplatePathBuildItem> templatePaths,
            TemplatesAnalysisBuildItem templatesAnalysis,
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

        Set<String> expressions = config.detectTemplateData ? collectExpressions(templatesAnalysis) : null;

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

        if (expressions != null) {
            controlled.addAll(detectTemplateData(combinedIndex.getIndex(), appClassPredicate, expressions));
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
        for (AnnotationInstance named : index.getAnnotations(DotNames.NAMED)) {
            if (named.target().kind() == Kind.CLASS) {
                generator.generate(named.target().asClass());
            }
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

    @BuildStep
    void collectTemplates(QuteConfig config, ApplicationArchivesBuildItem applicationArchivesBuildItem,
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
                produceTemplateBuildItems(templatePaths, watchedPaths, substrateResources, tagBasePath, tagPath, path, true);
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

    private Set<String> collectExpressions(TemplatesAnalysisBuildItem analysis) {
        Set<String> expressions = new HashSet<>();
        for (TemplateAnalysis template : analysis.getAnalysis()) {
            for (Expression expression : template.expressions) {
                if (expression.literal != null) {
                    continue;
                }
                Iterable<String> parts;
                if (expression.namespace != null && expression.parts.size() > 1) {
                    parts = expression.parts.subList(1, expression.parts.size());
                } else {
                    parts = expression.parts;
                }
                for (String part : parts) {
                    if (part.indexOf("(") == -1) {
                        expressions.add(part);
                    }
                }
            }
        }
        return expressions;
    }

    private Set<Expression> collectInjectExpressions(TemplatesAnalysisBuildItem analysis) {
        Set<Expression> injectExpressions = new HashSet<>();
        for (TemplateAnalysis template : analysis.getAnalysis()) {
            injectExpressions.addAll(collectInjectExpressions(template));
        }
        return injectExpressions;
    }

    private Set<Expression> collectInjectExpressions(TemplateAnalysis analysis) {
        Set<Expression> injectExpressions = new HashSet<>();
        for (Expression expression : analysis.expressions) {
            if (expression.literal != null) {
                continue;
            }
            if (EngineProducer.INJECT_NAMESPACE.equals(expression.namespace)) {
                injectExpressions.add(expression);
            }
        }
        return injectExpressions;
    }

    private Set<ClassInfo> detectTemplateData(IndexView index, Predicate<String> appClassPredicate,
            Set<String> expressions) {
        long start = System.currentTimeMillis();
        Set<ClassInfo> ret = new HashSet<>();

        for (ClassInfo classInfo : index.getKnownClasses()) {
            // skip interfaces, abstract classes, non-public and non-app classes
            if (Modifier.isPublic(classInfo.flags()) && !Modifier.isInterface(classInfo.flags())
                    && !Modifier.isAbstract(classInfo.flags()) && appClassPredicate.test(classInfo.name().toString())) {
                ClassInfo clazz = classInfo;
                while (clazz != null) {
                    if (matches(clazz, expressions)) {
                        ret.add(classInfo);
                    }
                    if (!clazz.superName().equals(DotNames.OBJECT)) {
                        clazz = index.getClassByName(clazz.superName());
                    } else {
                        clazz = null;
                    }
                }
            }
        }

        for (Iterator<ClassInfo> iterator = ret.iterator(); iterator.hasNext();) {
            ClassInfo classInfo = iterator.next();
            if (classInfo.classAnnotation(DotNames.NAMED) != null
                    && classInfo.classAnnotation(ValueResolverGenerator.TEMPLATE_DATA) != null
                    && classInfo.classAnnotation(ValueResolverGenerator.TEMPLATE_DATA_CONTAINER) != null) {
                // Remove classes annotated with @Named or @TemplateData
                iterator.remove();
            }
        }

        LOGGER.debug("Detected template data classes in {} ms: \n {}", System.currentTimeMillis() - start, ret);
        return ret;
    }

    private boolean matches(ClassInfo classInfo, Set<String> expressions) {
        return hasMatchingField(classInfo.fields(), expressions) || hasMatchingMethod(classInfo.methods(), expressions);
    }

    private boolean hasMatchingField(Iterable<FieldInfo> fields, Set<String> expressions) {
        for (FieldInfo field : fields) {
            if (!Modifier.isPublic(field.flags()) || Modifier.isStatic(field.flags())
                    || ValueResolverGenerator.isSynthetic(field.flags())) {
                continue;
            }
            if (expressions.contains(field.name())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMatchingMethod(Iterable<MethodInfo> methods, Set<String> expressions) {
        for (MethodInfo method : methods) {
            if (method.name().equals("<init>")
                    || method.name().equals("<clinit>") || ValueResolverGenerator.isSynthetic(method.flags())
                    || !Modifier.isPublic(method.flags())
                    || Modifier.isStatic(method.flags())
                    || method.returnType().kind() == org.jboss.jandex.Type.Kind.VOID
                    || !method.parameters().isEmpty()) {
                continue;
            }
            if (expressions.contains(method.name())
                    || expressions.contains(ValueResolverGenerator.getPropertyName(method.name()))) {
                return true;
            }
        }
        return false;
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
            BuildProducer<SubstrateResourceBuildItem> substrateResources, String basePath, String filePath, Path originalPath,
            boolean tag) {
        if (filePath.isEmpty()) {
            return;
        }
        String fullPath = basePath + filePath;
        watchedPaths.produce(new HotDeploymentWatchedFileBuildItem(fullPath, false));
        substrateResources.produce(new SubstrateResourceBuildItem(fullPath));
        templatePaths.produce(new TemplatePathBuildItem(filePath, originalPath, tag));
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
                produceTemplateBuildItems(templatePaths, watchedPaths, substrateResources, basePath, templatePath, path, false);
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
