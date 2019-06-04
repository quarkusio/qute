package com.github.mkouba.qute;

import static com.github.mkouba.qute.Parameter.EMPTY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Basic sequential {@code loop} statement.
 */
public class LoopSectionHelper implements SectionHelper {

    private final boolean noAlias;
    private final String alias;
    private final Expression iterable;

    public LoopSectionHelper(String alias, String iterable) {
        if (alias.equals(Parameter.EMPTY)) {
            this.noAlias = true;
            this.alias = null;
        } else {
            this.noAlias = false;
            this.alias = alias;
        }
        this.iterable = Expression.parse(Objects.requireNonNull(iterable));
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
        return context.resolutionContext().evaluate(iterable).thenCompose(it -> {
            // TODO ideally, we should not block here but we still need to retain the order of results 
            List<CompletionStage<ResultNode>> results = new ArrayList<>();
            Iterator<?> iterator;
            if (it instanceof Iterable) {
                iterator = ((Iterable<?>) it).iterator();
            } else if (it instanceof Map) {
                iterator = ((Map<?, ?>) it).entrySet().iterator();
            } else if (it instanceof Stream) {
                iterator = ((Stream<?>) it).sequential().iterator();
            } else {
                throw new IllegalStateException("Cannot iterate over: " + it);
            }
            int idx = 0;
            while (iterator.hasNext()) {
                results.add(nextElement(iterator.next(), idx++, iterator.hasNext(), context));
            }
            if (results.isEmpty()) {
                return CompletableFuture.completedFuture(ResultNode.NOOP);
            }
            CompletableFuture<ResultNode> result = new CompletableFuture<>();
            CompletableFuture<ResultNode>[] all = new CompletableFuture[results.size()];
            idx = 0;
            for (CompletionStage<ResultNode> r : results) {
                all[idx++] = r.toCompletableFuture();
            }
            CompletableFuture
                    .allOf(all)
                    .whenComplete((v, t) -> {
                        if (t != null) {
                            result.completeExceptionally(t);
                        } else {
                            result.complete(new MultiResultNode(all));
                        }
                    });
            return result;
        });
    }

    CompletionStage<ResultNode> nextElement(Object element, int index, boolean hasNext, SectionResolutionContext context) {
        AtomicReference<ResolutionContext> resolutionContextHolder = new AtomicReference<>();
        List<NamespaceResolver> namespaceResolvers = noAlias
                ? Collections.singletonList(new IterationMetaResolver(index, hasNext))
                : ImmutableList.of(new IterationMetaResolver(index, hasNext),
                        new AliasResolver(alias, resolutionContextHolder));
        ResolutionContext child = context.resolutionContext().createChild(element, namespaceResolvers);
        resolutionContextHolder.set(child);
        return context.execute(child);
    }

    public static class Factory implements SectionHelperFactory<LoopSectionHelper> {

        @Override
        public List<String> getDefaultAliases() {
            return ImmutableList.of("for", "each");
        }

        @Override
        public ParametersInfo getParameters() {
            return ParametersInfo.builder().addParameter("alias", EMPTY).addParameter("in", EMPTY)
                    .addParameter(new Parameter("iterable", null, true))
                    .build();
        }

        @Override
        public LoopSectionHelper initialize(SectionInitContext context) {
            String iterable = context.getParameter("iterable");
            if (iterable == null) {
                iterable = ValueResolvers.THIS;
            }
            return new LoopSectionHelper(context.getParameter("alias"), iterable);
        }

    }

    static class IterationMetaResolver implements NamespaceResolver {

        final int index;
        final boolean hasNext;

        public IterationMetaResolver(int index, boolean hasNext) {
            this.index = index;
            this.hasNext = hasNext;
        }

        @Override
        public String getNamespace() {
            return "iter";
        }

        @Override
        public CompletionStage<Object> resolve(EvalContext context) {
            switch (context.getName()) {
                case "count":
                    return CompletableFuture.completedFuture(index + 1);
                case "index":
                    return CompletableFuture.completedFuture(index);
                case "indexParity":
                    return CompletableFuture.completedFuture(index % 2 != 0 ? "even" : "odd");
                case "hasNext":
                    return CompletableFuture.completedFuture(hasNext);
                case "isOdd":
                case "odd":
                    return CompletableFuture.completedFuture(index % 2 == 0);
                case "isEven":
                case "even":
                    return CompletableFuture.completedFuture(index % 2 != 0);
                default:
                    return Results.NOT_FOUND;
            }
        }

    }

}