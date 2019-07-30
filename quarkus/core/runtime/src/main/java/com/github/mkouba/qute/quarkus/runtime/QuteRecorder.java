package com.github.mkouba.qute.quarkus.runtime;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mkouba.qute.Engine;

import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QuteRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuteRecorder.class);

    public void start(QuteConfig config, BeanContainer container, List<String> resolverClasses,
            List<String> templatePaths, List<String> tags) {
        TemplateProducer templateProducer = container.instance(TemplateProducer.class);
        templateProducer.init(config, resolverClasses, templatePaths, tags);
    }

    public static void clearTemplates(Set<String> paths) {
        TemplateProducer templateProducer = Arc.container().instance(TemplateProducer.class).get();
        Set<String> pathIds = paths.stream().map(path -> {
            String id = path;
            if (path.startsWith(templateProducer.getTagPath())) {
                // ["META-INF/resources/templates/tags/item.html"] -> ["item.html"]
                id = path.substring(templateProducer.getTagPath().length());
            } else if (path.startsWith(templateProducer.getBasePath())) {
                // ["META-INF/resources/templates/items.html"] -> ["items.html"]
                id = path.substring(templateProducer.getBasePath().length());
            }
            return id;
        }).collect(Collectors.toSet());

        if (pathIds.isEmpty()) {
            return;
        }

        Engine engine = templateProducer.getEngine();
        if (engine != null) {
            engine.removeTemplates(id -> {
                // Exact match or path starts with id, e.g. "items.html" starts with "items"
                boolean remove = pathIds.contains(id) || pathIds.stream().anyMatch(pid -> pid.startsWith(id));
                if (remove) {
                    LOGGER.info("Going to remove the template with id: {}", id);
                }
                return remove;
            });
        }
    }

}
