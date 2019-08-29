package io.quarkus.qute.rxjava;

import org.reactivestreams.Publisher;

import io.quarkus.qute.PublisherFactory;
import io.quarkus.qute.Template.Rendering;
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
