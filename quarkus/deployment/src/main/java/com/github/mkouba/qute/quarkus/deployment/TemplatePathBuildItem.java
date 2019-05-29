package com.github.mkouba.qute.quarkus.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class TemplatePathBuildItem extends MultiBuildItem {

    private final String path;

    public TemplatePathBuildItem(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
    
}
