package com.github.mkouba.qute;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
class EngineImpl implements Engine {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineImpl.class);

    private final Map<String, SectionHelperFactory<?>> sectionHelperFactories;
    private final List<ValueResolver> valueResolvers;
    private final List<NamespaceResolver> namespaceResolvers;
    private final Evaluator evaluator;
    private final Map<String, Template> templates;
    private final List<Function<String, Optional<Reader>>> locators;

    EngineImpl(Map<String, SectionHelperFactory<?>> sectionHelperFactories, List<ValueResolver> valueResolvers,
            List<NamespaceResolver> namespaceResolvers, List<Function<String, Optional<Reader>>> locators) {
        this.sectionHelperFactories = new HashMap<>(sectionHelperFactories);
        this.valueResolvers = ImmutableList.copyOf(valueResolvers);
        this.namespaceResolvers = ImmutableList.copyOf(namespaceResolvers);
        this.evaluator = new EvaluatorImpl(this.valueResolvers);
        this.templates = new ConcurrentHashMap<>();
        this.locators = ImmutableList.copyOf(locators);
    }

    public Template parse(String content) {
        return new Parser(this).parse(new StringReader(content));
    }

    public Map<String, SectionHelperFactory<?>> getSectionHelperFactories() {
        return sectionHelperFactories;
    }

    public List<ValueResolver> getValueResolvers() {
        return valueResolvers;
    }

    public List<NamespaceResolver> getNamespaceResolvers() {
        return namespaceResolvers;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public Template putTemplate(String id, Template template) {
        return templates.put(id, template);
    }

    public Template getTemplate(String id) {
        return templates.computeIfAbsent(id, this::load);
    }

    private Template load(String id) {
        for (Function<String, Optional<Reader>> locator : locators) {
            Optional<Reader> reader = locator.apply(id);
            if (reader.isPresent()) {
                try {
                    return new Parser(this).parse(reader.get());
                } finally {
                    try {
                        reader.get().close();
                    } catch (IOException e) {
                        LOGGER.warn("Unable to close the reader for " + id, e);
                    }
                }
            }
        }
        return null;
    }

}
