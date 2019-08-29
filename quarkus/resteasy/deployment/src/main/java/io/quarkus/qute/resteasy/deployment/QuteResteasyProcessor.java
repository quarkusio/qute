package io.quarkus.qute.resteasy.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.qute.resteasy.TemplateResponseFilter;
import io.quarkus.resteasy.common.deployment.ResteasyJaxrsProviderBuildItem;

public class QuteResteasyProcessor {

    @BuildStep
    ResteasyJaxrsProviderBuildItem registerProviders() {
        return new ResteasyJaxrsProviderBuildItem(TemplateResponseFilter.class.getName());
    }

}
