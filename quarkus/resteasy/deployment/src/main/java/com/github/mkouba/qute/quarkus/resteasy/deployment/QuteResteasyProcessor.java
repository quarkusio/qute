package com.github.mkouba.qute.quarkus.resteasy.deployment;

import com.github.mkouba.qute.quarkus.resteasy.TemplateResponseFilter;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.resteasy.common.deployment.ResteasyJaxrsProviderBuildItem;

public class QuteResteasyProcessor {

    @BuildStep
    ResteasyJaxrsProviderBuildItem registerProviders() {
        return new ResteasyJaxrsProviderBuildItem(TemplateResponseFilter.class.getName());
    }

}
