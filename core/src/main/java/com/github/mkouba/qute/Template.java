package com.github.mkouba.qute;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * A compiled template.
 */
public interface Template {

    /**
     * 
     * @param data
     * @return the rendered template
     */
    String render(Object data);

    /**
     * 
     * @param data
     * @param resultConsumer
     * @return the new CompletionStage
     */
    CompletionStage<Void> render(Object data, Consumer<String> resultConsumer);

}
