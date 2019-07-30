package com.github.mkouba.qute.quarkus.resteasy;

import java.util.List;
import java.util.Map;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QuteResteasyRecorder {

    public void start(BeanContainer container, Map<String, List<String>> variants) {
        TemplateVariantProducer variantProducer = container.instance(TemplateVariantProducer.class);
        variantProducer.init(variants);
    }

}
