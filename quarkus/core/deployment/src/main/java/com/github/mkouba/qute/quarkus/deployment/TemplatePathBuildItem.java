package com.github.mkouba.qute.quarkus.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class TemplatePathBuildItem extends MultiBuildItem {

    private final String path;
    private final boolean tag;

    public TemplatePathBuildItem(String path) {
       this(path, false);
    }

    public TemplatePathBuildItem(String path, boolean tag) {
        this.path = path;
        this.tag = tag;
    }

    public String getPath() {
        return path;
    }

    public boolean isTag() {
        return tag;
    }
    
    public boolean isRegular() {
        return !isTag();
    }
    
}
