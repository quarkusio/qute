package com.github.mkouba.qute;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * 
 */
class TemplateImpl implements Template {

    private final EngineImpl engine;
    final SectionNode root;

    public TemplateImpl(EngineImpl engine, SectionNode root) {
        this.engine = engine;
        this.root = root;
    }

    @Override
    public String render(Object data) {
        StringBuilder builder = new StringBuilder();
        try {
            renderData(data, builder::append).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
        return builder.toString();
    }

    @Override
    public CompletionStage<Void> render(Object data, Consumer<String> resultConsumer) {
        return renderData(data, resultConsumer);
    }

    private CompletionStage<Void> renderData(Object data, Consumer<String> resultConsumer) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        DataNamespaceResolver dataResolver = new DataNamespaceResolver();
        List<NamespaceResolver> namespaceResolvers = ImmutableList.<NamespaceResolver> builder()
                .addAll(engine.getNamespaceResolvers()).add(dataResolver).build();
        ResolutionContext rootContext = new ResolutionContextImpl(null, data, namespaceResolvers,
                engine.getEvaluator(), null);
        dataResolver.rootContext = rootContext;
        // Async resolution
        root.resolve(rootContext).whenComplete((r, t) -> {
            if (t != null) {
                result.completeExceptionally(t);
            } else {
                // Sync processing of the result tree - build the output
                r.process(resultConsumer);
                result.complete(null);
            }
        });
        return result;
    }

    static class DataNamespaceResolver implements NamespaceResolver {

        ResolutionContext rootContext;

        @Override
        public CompletionStage<Object> resolve(EvalContext context) {
            return rootContext.evaluate(context.getName());
        }

        @Override
        public String getNamespace() {
            return "data";
        }

    }

}