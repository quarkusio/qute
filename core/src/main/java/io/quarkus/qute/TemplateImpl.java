package io.quarkus.qute;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.reactivestreams.Publisher;

class TemplateImpl implements Template {

    private final String generatedId;
    private final EngineImpl engine;
    final SectionNode root;

    TemplateImpl(EngineImpl engine, SectionNode root, String generatedId) {
        this.engine = engine;
        this.root = root;
        this.generatedId = generatedId;
    }

    @Override
    public Rendering render() {
        return new RenderingImpl();
    }

    @Override
    public Set<Expression> getExpressions() {
        return root.getExpressions();
    }
    
    @Override
    public String getGeneratedId() {
        return generatedId;
    }

    private class RenderingImpl extends RenderingBase {

        @Override
        public String getResult() {
            try {
                Object timeoutAttr = getAttribute(TIMEOUT);
                long timeout = timeoutAttr != null ? Long.parseLong(timeoutAttr.toString()) : 10000;
                return getResultAsync().toCompletableFuture().get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Publisher<String> publisher() {
            PublisherFactory factory = engine.getPublisherFactory();
            if (factory == null) {
                throw new UnsupportedOperationException();
            }
            return factory.createPublisher(this);
        }

        @Override
        public CompletionStage<String> getResultAsync() {
            StringBuilder builder = new StringBuilder();
            return renderData(data(), builder::append).thenApply(v -> builder.toString());
        }

        @Override
        public CompletionStage<Void> consume(Consumer<String> resultConsumer) {
            return renderData(data(), resultConsumer);
        }

    }

    private CompletionStage<Void> renderData(Object data, Consumer<String> consumer) {
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
                r.process(consumer);
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