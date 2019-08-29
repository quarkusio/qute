package io.quarkus.qute.deployment;

import io.quarkus.deployment.devmode.HotReplacementContext;
import io.quarkus.deployment.devmode.HotReplacementSetup;
import io.quarkus.qute.runtime.QuteRecorder;

public class QuteHotReplacementSetup implements HotReplacementSetup {

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        context.consumeNoRestartChanges(QuteRecorder::clearTemplates);
    }

}
