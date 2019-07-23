package com.github.mkouba.qute.quarkus.runtime;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.mkouba.qute.Engine;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QuteRecorder {

    static volatile Engine engine;

    public void start(BeanContainer container, List<String> resolverClasses, List<String> templatePaths, List<String> tags) {
        TemplateProducer templateProducer = container.instance(TemplateProducer.class);
        engine = templateProducer.init(resolverClasses, templatePaths, tags);
    }

    public static void clearTemplates(Set<String> paths) {
        // ["META-INF/resources/items.html"] -> ["items.html"]
        Set<String> pathIds = paths.stream().map(p -> {
            if (p.startsWith(TemplateProducer.METAINF_RESOURCES)) {
                return p.substring(TemplateProducer.METAINF_RESOURCES.length());
            }
            return p;
        }).collect(Collectors.toSet());
        engine.removeTemplates(id -> {
            // Exact match or path starts with id, e.g. "items.html" starts with "items"
            return pathIds.contains(id) || pathIds.stream().anyMatch(pid -> pid.startsWith(id));
        });
    }

}
