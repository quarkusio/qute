package com.github.mkouba.qute;

import java.util.concurrent.CompletionStage;


public interface Resolver {
    
    /**
     * 
     * @param context
     * @return the result
     * @see Results#NOT_FOUND
     */
    CompletionStage<Object> resolve(EvalContext context);

}
