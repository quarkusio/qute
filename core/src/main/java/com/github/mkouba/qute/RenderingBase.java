package com.github.mkouba.qute;

import java.util.HashMap;
import java.util.Map;

import com.github.mkouba.qute.Template.Rendering;

public abstract class RenderingBase implements Rendering {

    protected Object data;
    protected Map<String, Object> dataMap;
    protected Map<String, Object> attributes;
    
    public RenderingBase() {
        this.attributes = new HashMap<>();
    }

    @Override
    public Rendering setData(Object data) {
        this.data = data;
        dataMap = null;
        return this;
    }

    @Override
    public Rendering putData(String key, Object data) {
        this.data = null;
        if (dataMap == null) {
            dataMap = new HashMap<String, Object>();
        }
        dataMap.put(key, data);
        return this;
    }
    
    @Override
    public Rendering putAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    protected Object data() {
        return data != null ? data : dataMap;
    }

    
}
