package com.github.mkouba.qute;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

class Futures {

    static <T> CompletableFuture<T> failure(Throwable t) {
        CompletableFuture<T> failure = new CompletableFuture<>();
        failure.completeExceptionally(t);
        return failure;
    }
    
    @SuppressWarnings("unchecked")
    static CompletionStage<Map<String, Object>>  evaluateParams(Map<String, Expression> parameters, ResolutionContext resolutionContext) {
        CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        CompletableFuture<Object>[] results = new CompletableFuture[parameters.size()];
        int idx = 0;
        for (Entry<String, Expression> entry : parameters.entrySet()) {
            results[idx++] = resolutionContext.evaluate(entry.getValue()).toCompletableFuture();
        }
        CompletableFuture.allOf(results).whenComplete((v, t1) -> {
            if (t1 != null) {
                result.completeExceptionally(t1);
            } else {
                // Build a map from the params
                Map<String, Object> paramValues = new HashMap<>();
                int j = 0;
                try {
                    for (Entry<String, Expression> entry : parameters.entrySet()) {
                        paramValues.put(entry.getKey(), results[j++].get());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw new IllegalStateException(e);
                }
                result.complete(paramValues);
            }
        });
        return result;
    }
}
