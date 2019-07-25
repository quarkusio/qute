package com.github.mkouba.qute;

import org.reactivestreams.Publisher;

import com.github.mkouba.qute.Template.Rendering;

/**
 * Service provider.
 */
public interface PublisherFactory {
    
    Publisher<String> createPublisher(Rendering rendering);

}
