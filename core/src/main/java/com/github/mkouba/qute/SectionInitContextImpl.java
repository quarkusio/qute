package com.github.mkouba.qute;

import java.util.List;
import java.util.Map;

import com.github.mkouba.qute.SectionHelperFactory.SectionInitContext;

/**
 * 
 */
final class SectionInitContextImpl implements SectionInitContext {

    private final EngineImpl engine;
    private final List<SectionBlock> blocks;

    public SectionInitContextImpl(EngineImpl engine, List<SectionBlock> blocks) {
        this.engine = engine;
        this.blocks = blocks;
    }
    
    /**
     * 
     * @return the params of the main block
     */
    public Map<String, String> getParameters() {
        return blocks.get(0).parameters;
    }

    public boolean hasParameter(String name) {
        return getParameters().containsKey(name);
    }

    public String getParameter(String name) {
        return getParameters().get(name);
    }

    public List<SectionBlock> getBlocks() {
        return blocks;
    }

    @Override
    public EngineImpl getEngine() {
        return engine;
    }

}