package com.github.mkouba.qute;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.reactivestreams.Publisher;

/**
 * Compiled template.
 */
public interface Template {

    /**
     * Convenient method to render a template as string.
     * 
     * @param data
     * @return the rendered template
     */
    default String render(Object data) {
        return render().setData(data).getResult();
    }

    /**
     * 
     * @return a new rendering action
     */
    Rendering render();

    /**
     * This construct is not thread-safe.
     */
    interface Rendering {

        /**
         * Set the the root context object. Invocation of this method removes a map produced previously by
         * {@link #putData(String, Object)}.
         * 
         * @param data
         * @return
         */
        Rendering setData(Object data);

        /**
         * Put the data in a map. The resulting map will be used as the root context object during rendering. Invocation of this
         * method removes the root context object previously set by {@link #setData(Object)}.
         * 
         * @param key
         * @param data
         * @return self
         */
        Rendering putData(String key, Object data);

        /**
         * 
         * @param key
         * @param value
         * @return self
         */
        Rendering putAttribute(String key, Object value);

        /**
         * 
         * @param key
         * @return the attribute or null
         */
        Object getAttribute(String key);

        /**
         * Triggers rendering.
         * 
         * @return the rendered template as string
         */
        String getResult();

        /**
         * Triggers rendering.
         * 
         * @return a completion stage that is completed once the rendering finished
         */
        CompletionStage<String> getResultAsync();

        /**
         * Each subscription triggers rendering.
         * 
         * @return a publisher that can be used to consume chunks of the rendered template
         * @throws UnsupportedOperationException If no {@link PublisherFactory} service provider is found
         */
        Publisher<String> publisher();

        /**
         * Triggers rendering.
         * 
         * @param consumer To consume chunks of the rendered template
         * @return a completion stage that is completed once the rendering finished
         */
        CompletionStage<Void> consume(Consumer<String> consumer);

    }

}
