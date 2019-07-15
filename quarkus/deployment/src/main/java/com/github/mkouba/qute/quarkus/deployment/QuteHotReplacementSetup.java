package com.github.mkouba.qute.quarkus.deployment;

import com.github.mkouba.qute.quarkus.runtime.QuteRecorder;

import io.quarkus.deployment.devmode.HotReplacementContext;
import io.quarkus.deployment.devmode.HotReplacementSetup;

public class QuteHotReplacementSetup implements HotReplacementSetup {

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        context.consumeNoRestartChanges(QuteRecorder::clearTemplates);
    }

}
