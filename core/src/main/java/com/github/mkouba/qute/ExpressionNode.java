package com.github.mkouba.qute;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This node holds a single expression such as {@code foo.bar}.
 */
class ExpressionNode implements TemplateNode {

    private final Expression expression;

    public ExpressionNode(String value) {
        this.expression = Expression.parse(value);
    }

    @Override
    public CompletionStage<ResultNode> resolve(ResolutionContext context) {
        return context.evaluate(expression)
                .thenCompose(r -> CompletableFuture.<ResultNode> completedFuture(new SingleResultNode(r)));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ExpressionNode [expression=").append(expression).append("]");
        return builder.toString();
    }

}
