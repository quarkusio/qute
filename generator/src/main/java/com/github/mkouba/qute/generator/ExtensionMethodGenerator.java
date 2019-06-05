package com.github.mkouba.qute.generator;

import static com.github.mkouba.qute.generator.ValueResolverGenerator.generatedNameFromTarget;
import static com.github.mkouba.qute.generator.ValueResolverGenerator.packageName;
import static com.github.mkouba.qute.generator.ValueResolverGenerator.simpleName;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type.Kind;

import com.github.mkouba.qute.EvalContext;
import com.github.mkouba.qute.ValueResolver;

import io.quarkus.gizmo.AssignableResultHandle;
import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FunctionCreator;
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

        // Validate the method first
        if (!Modifier.isStatic(method.flags())) {
            throw new IllegalStateException("Template extension method must be static: " + method);
        }
        if (method.returnType().kind() == Kind.VOID) {
            throw new IllegalStateException("Template extension method must not return void: " + method);
        }
        if (method.parameters().isEmpty()) {
            throw new IllegalStateException("Template extension method must declare at least one parameter: " + method);
        }

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

        ResultHandle evalContext = resolve.getMethodParam(0);
        ResultHandle base = resolve.invokeInterfaceMethod(Descriptors.GET_BASE, evalContext);

        ResultHandle ret;
        int paramSize = method.parameters().size();
        if (paramSize == 1) {
            ret = resolve.invokeStaticMethod(Descriptors.COMPLETED_FUTURE, resolve
                    .invokeStaticMethod(MethodDescriptor.ofMethod(declaringClass.name().toString(), method.name(),
                            method.returnType().name().toString(),
                            method.parameters().get(0).name().toString()), base));
        } else {
            ret = resolve
                    .newInstance(MethodDescriptor.ofConstructor(CompletableFuture.class));

            // Evaluate params first
            ResultHandle params = resolve.invokeInterfaceMethod(Descriptors.GET_PARAMS, evalContext);
            ResultHandle resultsArray = resolve.newArray(CompletableFuture.class,
                    resolve.load(paramSize - 1));
            for (int i = 0; i < (paramSize - 1); i++) {
                ResultHandle evalResult = resolve.invokeInterfaceMethod(
                        Descriptors.EVALUATE, evalContext,
                        resolve.invokeInterfaceMethod(Descriptors.LIST_GET, params,
                                resolve.load(i)));
                resolve.writeArrayValue(resultsArray, i,
                        resolve.invokeInterfaceMethod(Descriptors.CF_TO_COMPLETABLE_FUTURE, evalResult));
            }
            ResultHandle allOf = resolve.invokeStaticMethod(Descriptors.COMPLETABLE_FUTURE_ALL_OF,
                    resultsArray);

            FunctionCreator whenCompleteFun = resolve.createFunction(BiConsumer.class);
            resolve.invokeInterfaceMethod(Descriptors.CF_WHEN_COMPLETE, allOf, whenCompleteFun.getInstance());
            BytecodeCreator whenComplete = whenCompleteFun.getBytecode();
            AssignableResultHandle whenBase = whenComplete.createVariable(Object.class);
            whenComplete.assign(whenBase, base);
            AssignableResultHandle whenRet = whenComplete.createVariable(CompletableFuture.class);
            whenComplete.assign(whenRet, ret);
            AssignableResultHandle whenResults = whenComplete.createVariable(CompletableFuture[].class);
            whenComplete.assign(whenResults, resultsArray);

            BranchResult throwableIsNull = whenComplete.ifNull(whenComplete.getMethodParam(1));

            BytecodeCreator success = throwableIsNull.trueBranch();

            ResultHandle[] args = new ResultHandle[paramSize];
            args[0] = whenBase;
            for (int i = 0; i < (paramSize - 1); i++) {
                ResultHandle paramResult = success.readArrayValue(whenResults, i);
                args[i + 1] = success.invokeVirtualMethod(Descriptors.COMPLETABLE_FUTURE_GET, paramResult);
            }

            ResultHandle invokeRet = success
                    .invokeStaticMethod(MethodDescriptor.ofMethod(declaringClass.name().toString(), method.name(),
                            method.returnType().name().toString(),
                            method.parameters().stream().map(p -> p.name().toString()).collect(Collectors.toList()).toArray()),
                            args);
            success.invokeVirtualMethod(Descriptors.COMPLETABLE_FUTURE_COMPLETE, whenRet, invokeRet);

            BytecodeCreator failure = throwableIsNull.falseBranch();
            failure.invokeVirtualMethod(Descriptors.COMPLETABLE_FUTURE_COMPLETE_EXCEPTIONALLY, whenRet,
                    whenComplete.getMethodParam(1));
            whenComplete.returnValue(null);
        }

        resolve.returnValue(ret);
    }

    private void implementAppliesTo(ClassCreator valueResolver, MethodInfo method) {
        MethodCreator appliesTo = valueResolver.getMethodCreator("appliesTo", boolean.class, EvalContext.class)
                .setModifiers(ACC_PUBLIC);

        ResultHandle evalContext = appliesTo.getMethodParam(0);
        ResultHandle base = appliesTo.invokeInterfaceMethod(Descriptors.GET_BASE, evalContext);
        ResultHandle name = appliesTo.invokeInterfaceMethod(Descriptors.GET_NAME, evalContext);
        BytecodeCreator baseNull = appliesTo.ifNull(base).trueBranch();
        baseNull.returnValue(baseNull.load(false));

        // Test base object class
        ResultHandle baseClass = appliesTo.invokeVirtualMethod(Descriptors.GET_CLASS, base);
        ResultHandle testClass = appliesTo.loadClass(method.parameters().get(0).name().toString());
        ResultHandle baseClassTest = appliesTo.invokeVirtualMethod(Descriptors.IS_ASSIGNABLE_FROM, testClass,
                baseClass);
        BytecodeCreator baseNotAssignable = appliesTo.ifNonZero(baseClassTest).falseBranch();
        baseNotAssignable.returnValue(baseNotAssignable.load(false));

        // Test property name
        ResultHandle nameTest = appliesTo.invokeVirtualMethod(Descriptors.EQUALS, name,
                appliesTo.load(method.name()));
        BytecodeCreator nameNotMatched = appliesTo.ifNonZero(nameTest).falseBranch();
        nameNotMatched.returnValue(nameNotMatched.load(false));

        int paramSize = method.parameters().size();
        if (paramSize > 1) {
            // Test number of parameters
            ResultHandle params = appliesTo.invokeInterfaceMethod(Descriptors.GET_PARAMS, evalContext);
            ResultHandle paramsCount = appliesTo.invokeInterfaceMethod(Descriptors.COLLECTION_SIZE, params);
            BranchResult paramsTest = appliesTo
                    .ifNonZero(appliesTo.invokeStaticMethod(Descriptors.INTEGER_COMPARE,
                            appliesTo.load(paramSize - 1), paramsCount));
            BytecodeCreator paramsNotMatching = paramsTest.trueBranch();
            paramsNotMatching.returnValue(paramsNotMatching.load(false));
        }
        appliesTo.returnValue(appliesTo.load(true));
    }

}
