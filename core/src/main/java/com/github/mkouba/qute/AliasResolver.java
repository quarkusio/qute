package com.github.mkouba.qute;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

class AliasResolver implements NamespaceResolver {

    private final AtomicReference<ResolutionContext> resolutionContextHolder;
    private final String alias;

    public AliasResolver(String alias, AtomicReference<ResolutionContext> resolutionContextHolder) {
        this.alias = alias;
        this.resolutionContextHolder = resolutionContextHolder;
    }

    @Override
    public CompletionStage<Object> resolve(EvalContext context) {
        return resolutionContextHolder.get().evaluate(Expression.single(context.getName()));
    }

    @Override
    public String getNamespace() {
        return alias;
    }

}