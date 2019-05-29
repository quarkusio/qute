package com.github.mkouba.qute;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Static text.
 */
public class TextNode implements TemplateNode, ResultNode {

    private final CompletableFuture<ResultNode> result;

    private final String value;

    public TextNode(String value) {
        this.result = CompletableFuture.completedFuture(this);
        this.value = value;
    }

    @Override
    public CompletionStage<ResultNode> resolve(ResolutionContext context) {
        return result;
    }

    @Override
    public void process(Consumer<String> consumer) {
        consumer.accept(value);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TextNode [value=").append(value).append("]");
        return builder.toString();
    }

}