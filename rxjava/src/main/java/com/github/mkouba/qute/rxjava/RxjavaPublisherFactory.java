package com.github.mkouba.qute.rxjava;

import org.reactivestreams.Publisher;

import com.github.mkouba.qute.PublisherFactory;
import com.github.mkouba.qute.Template.Rendering;

import io.reactivex.Flowable;
import io.reactivex.processors.UnicastProcessor;

public class RxjavaPublisherFactory implements PublisherFactory {

    @Override
    public Publisher<String> createPublisher(Rendering rendering) {
        return Flowable.defer(() -> {
            UnicastProcessor<String> processor = UnicastProcessor.create();
            rendering.consume(s -> processor.onNext(s))
                    .whenComplete((v, t) -> {
                        if (t == null) {
                            processor.onComplete();
                        } else {
                            processor.onError(t);
                        }
                    });
            return processor;
        });
    }

}
