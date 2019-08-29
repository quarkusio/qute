package io.quarkus.qute;

import org.reactivestreams.Publisher;

import io.quarkus.qute.Template.Rendering;

/**
 * Service provider.
 */
public interface PublisherFactory {
    
    Publisher<String> createPublisher(Rendering rendering);

}
