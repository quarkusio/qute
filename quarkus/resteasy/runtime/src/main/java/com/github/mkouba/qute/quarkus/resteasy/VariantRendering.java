package com.github.mkouba.qute.quarkus.resteasy;

import com.github.mkouba.qute.Template;

public interface VariantRendering extends Template.Rendering {
    
    String getBaseName();

    String getSelectedVariant();
    
    void selectVariant(javax.ws.rs.core.Request request);

}
