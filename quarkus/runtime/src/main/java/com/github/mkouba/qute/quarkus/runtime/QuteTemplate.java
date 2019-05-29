package com.github.mkouba.qute.quarkus.runtime;

import java.util.List;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Template;

@Template
public class QuteTemplate {

    public void start(BeanContainer container, List<String> resolverClasses, List<String> templatePaths) {
        TemplateProducer templateProducer = container.instance(TemplateProducer.class);
        templateProducer.init(resolverClasses, templatePaths);
    }

}
