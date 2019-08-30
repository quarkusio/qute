package io.quarkus.qute;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This node holds a single expression such as {@code foo.bar}.
 */
class ExpressionNode implements TemplateNode {

    private final Expression expression;
    private final Engine engine;

    public ExpressionNode(String value, Engine engine) {
        this.expression = Expression.parse(value);
        this.engine = engine;
    }

    @Override
    public CompletionStage<ResultNode> resolve(ResolutionContext context) {
        return context.evaluate(expression)
                .thenCompose(r -> CompletableFuture.<ResultNode> completedFuture(new SingleResultNode(r, this)));
    }

    Engine getEngine() {
        return engine;
    }
    
    Expression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ExpressionNode [expression=").append(expression).append("]");
        return builder.toString();
    }

}
