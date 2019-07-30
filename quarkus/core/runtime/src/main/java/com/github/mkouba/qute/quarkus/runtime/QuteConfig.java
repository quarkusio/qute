package com.github.mkouba.qute.quarkus.runtime;

import java.util.List;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class QuteConfig {
    
    /**
     * "META-INF/resources/${basePath}"
     */
    @ConfigItem(defaultValue = "templates")
    public String basePath;
    
    /**
     * TODO
     */
    @ConfigItem(defaultValue = "html,txt")
    public List<String> suffixes;

}
