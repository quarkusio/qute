package com.github.mkouba.qute.generator;

import static com.github.mkouba.qute.generator.ValueResolverGenerator.generatedNameFromTarget;
import static com.github.mkouba.qute.generator.ValueResolverGenerator.packageName;
import static com.github.mkouba.qute.generator.ValueResolverGenerator.simpleName;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;

import com.github.mkouba.qute.EvalContext;
import com.github.mkouba.qute.ValueResolver;

import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

public class ExtensionMethodGenerator {

    public static final String SUFFIX = "_Extension" + ValueResolverGenerator.SUFFIX;

    private final Set<String> generatedTypes;
    private final ClassOutput classOutput;

    public ExtensionMethodGenerator(ClassOutput classOutput) {
        this.classOutput = classOutput;
        this.generatedTypes = new HashSet<>();
    }

    public Set<String> getGeneratedTypes() {
        return generatedTypes;
    }

    public void generate(MethodInfo method) {

        ClassInfo declaringClass = method.declaringClass();
        String baseName;
        if (declaringClass.enclosingClass() != null) {
            baseName = simpleName(declaringClass.enclosingClass()) + "_" + simpleName(declaringClass);
        } else {
            baseName = simpleName(declaringClass);
        }
        String targetPackage = packageName(declaringClass.name());
        String generatedName = generatedNameFromTarget(targetPackage, baseName, "_" + method.name() + SUFFIX);
        generatedTypes.add(generatedName);

        ClassCreator valueResolver = ClassCreator.builder().classOutput(classOutput).className(generatedName)
                .interfaces(ValueResolver.class).build();

        implementAppliesTo(valueResolver, method);
        implementResolve(valueResolver, declaringClass, method);

        valueResolver.close();
    }

    private void implementResolve(ClassCreator valueResolver, ClassInfo declaringClass, MethodInfo method) {
        MethodCreator resolve = valueResolver.getMethodCreator("resolve", CompletionStage.class, EvalContext.class)
                .setModifiers(ACC_PUBLIC);

        ResultHandle valueContext = resolve.getMethodParam(0);
        ResultHandle base = resolve.invokeInterfaceMethod(Descriptors.GET_BASE, valueContext);

        ResultHandle ret = resolve
                .invokeStaticMethod(MethodDescriptor.ofMethod(declaringClass.name().toString(), method.name(), Object.class,
                        method.parameters().get(0).name().toString()), base);

        resolve.returnValue(resolve.invokeStaticMethod(Descriptors.COMPLETED_FUTURE, ret));
    }

    private void implementAppliesTo(ClassCreator valueResolver, MethodInfo method) {
        MethodCreator appliesTo = valueResolver.getMethodCreator("appliesTo", boolean.class, EvalContext.class)
                .setModifiers(ACC_PUBLIC);

        ResultHandle valueContext = appliesTo.getMethodParam(0);
        ResultHandle base = appliesTo.invokeInterfaceMethod(Descriptors.GET_BASE, valueContext);
        ResultHandle name = appliesTo.invokeInterfaceMethod(Descriptors.GET_NAME, valueContext);
        BranchResult baseTest = appliesTo.ifNull(base);
        BytecodeCreator baseNotNullBranch = baseTest.falseBranch();

        // Test base object class
        ResultHandle baseClass = baseNotNullBranch.invokeVirtualMethod(Descriptors.GET_CLASS, base);
        ResultHandle testClass = baseNotNullBranch.loadClass(method.parameters().get(0).name().toString());
        ResultHandle baseClassTest = baseNotNullBranch.invokeVirtualMethod(Descriptors.IS_ASSIGNABLE_FROM, testClass,
                baseClass);
        BytecodeCreator baseAssignableBranch = baseNotNullBranch.ifNonZero(baseClassTest).trueBranch();
        baseAssignableBranch.returnValue(baseAssignableBranch.load(true));

        // Test property name
        ResultHandle nameTest = baseNotNullBranch.invokeVirtualMethod(Descriptors.EQUALS, name,
                baseNotNullBranch.load(method.name()));
        BytecodeCreator nameMatchBranch = baseNotNullBranch.ifNonZero(nameTest).trueBranch();
        nameMatchBranch.returnValue(nameMatchBranch.load(true));

        appliesTo.returnValue(appliesTo.load(false));
    }

}
