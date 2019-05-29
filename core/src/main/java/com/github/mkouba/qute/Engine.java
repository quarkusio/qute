package com.github.mkouba.qute;

import java.util.List;
import java.util.Map;

/**
 * Template engine configuration.
 */
public interface Engine {

    static EngineBuilder builder() {
        return new EngineBuilder();
    }

    public Template parse(String content);

    public Map<String, SectionHelperFactory<?>> getSectionHelperFactories();

    public List<ValueResolver> getValueResolvers();

    public List<NamespaceResolver> getNamespaceResolvers();

    public Evaluator getEvaluator();

    /**
     *
     * @param id
     * @param template
     * @return the previous value or null
     */
    public Template putTemplate(String id, Template template);

    /**
     * Obtain a compiled template for the given id. The template could be registered using
     * {@link #putTemplate(String, Template)} or loaded by a template locator.
     * 
     * @param id
     * @return the template or null
     * @see EngineBuilder#addLocator(java.util.function.Function)
     */
    public Template getTemplate(String id);

}
