package com.github.mkouba.qute;

import java.util.function.Consumer;

/**
 * 
 */
public class SingleResultNode implements ResultNode {

    private final Object value;

    public SingleResultNode(Object value) {
        this.value = value;
    }

    @Override
    public void process(Consumer<String> consumer) {
        if (value != null) {
            consumer.accept(value.toString());
        }
    }

}
