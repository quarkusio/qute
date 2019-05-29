package com.github.mkouba.qute;

import java.util.concurrent.CompletionStage;

/**
 * Node of the template tree.
 */
public interface TemplateNode {

    /**
     * 
     * @param context
     * @return the result node
     */
    CompletionStage<ResultNode> resolve(ResolutionContext context);

}
