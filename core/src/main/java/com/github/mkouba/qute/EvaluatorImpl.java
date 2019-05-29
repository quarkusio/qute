package com.github.mkouba.qute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mkouba.qute.Results.Result;

/**
 * TODO get rid of iterators and prepare for parallel processing
 */
class EvaluatorImpl implements Evaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluatorImpl.class);

    private final List<ValueResolver> valueResolvers;

    EvaluatorImpl(List<ValueResolver> valueResolvers) {
        this.valueResolvers = valueResolvers;
    }

    @Override
    public CompletionStage<Object> evaluate(Expression expression, ResolutionContext resolutionContext) {
        Iterator<String> parts;
        if (expression.namespace != null) {
            parts = expression.parts.iterator();
            NamespaceResolver resolver = findNamespaceResolver(expression.namespace, resolutionContext);
            if (resolver == null) {
                LOGGER.error("No namespace resolver found for: {}", expression.namespace);
                return Futures.failure(new IllegalStateException("No resolver for namespace: " + expression.namespace));
            }
            EvalContext context = new EvalContextImpl(null, parts.next(), resolutionContext);
            LOGGER.debug("Found '{}' namespace resolver: {}", expression.namespace, resolver.getClass());
            return resolver.resolve(context).thenCompose(r -> {
                if (parts.hasNext()) {
                    return resolveReference(r, parts, valueResolvers.iterator(), resolutionContext);
                } else {
                    return CompletableFuture.completedFuture(r);
                }
            });
        } else {
            if (expression.literal != null) {
                return expression.literal;
            } else {
                parts = expression.parts.iterator();
                return resolveReference(resolutionContext.getData(), parts, valueResolvers.iterator(), resolutionContext);
            }
        }
    }

    private NamespaceResolver findNamespaceResolver(String namespace, ResolutionContext resolutionContext) {
        if (resolutionContext == null) {
            return null;
        }
        for (NamespaceResolver resolver : resolutionContext.getNamespaceResolvers()) {
            if (resolver.getNamespace().equals(namespace)) {
                return resolver;
            }
        }
        return findNamespaceResolver(namespace, resolutionContext.getParent());
    }

    private CompletionStage<Object> resolveReference(Object ref, Iterator<String> parts,
            Iterator<ValueResolver> resolvers, ResolutionContext resolutionContext) {
        return resolve(new EvalContextImpl(ref, parts.next(), resolutionContext), resolvers).thenCompose(r -> {
            if (parts.hasNext()) {
                return resolveReference(r, parts, valueResolvers.iterator(), resolutionContext);
            } else {
                return CompletableFuture.completedFuture(r);
            }
        });
    }

    private CompletionStage<Object> resolve(EvalContextImpl valueContext, Iterator<ValueResolver> resolvers) {
        if (!resolvers.hasNext()) {
            return Results.NOT_FOUND;
        }
        ValueResolver resolver = resolvers.next();
        if (resolver.appliesTo(valueContext)) {
            return resolver.resolve(valueContext).thenCompose(r -> {
                if (Result.NOT_FOUND.equals(r)) {
                    return resolve(valueContext, resolvers);
                } else {
                    return CompletableFuture.completedFuture(r);
                }
            });
        } else {
            // Try next resolver
            return resolve(valueContext, resolvers);
        }
    }

    class EvalContextImpl implements EvalContext {

        final Object base;
        final String name;
        final List<String> params;
        final ResolutionContext resolutionContext;

        public EvalContextImpl(Object base, String name, ResolutionContext resolutionContext) {
            this.base = base;
            this.resolutionContext = resolutionContext;
            int start = name.indexOf("(");
            if (start != -1 && name.endsWith(")")) {
                List<String> params = new ArrayList<>();
                // TODO string literals?
                for (String param : name.substring(start + 1, name.length() - 1).split(",")) {
                    params.add(param.trim());
                }
                this.params = ImmutableList.copyOf(params);
                this.name = name.substring(0, start);
            } else {
                this.params = Collections.emptyList();
                this.name = name;
            }
        }

        @Override
        public Object getBase() {
            return base;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getParams() {
            return params;
        }

        @Override
        public CompletionStage<Object> evaluate(Expression expression) {
            return resolutionContext.evaluate(expression);
        }

    }

}
