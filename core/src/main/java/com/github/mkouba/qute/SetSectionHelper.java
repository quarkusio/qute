package com.github.mkouba.qute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Basic {@code set} statement.
 */
public class SetSectionHelper implements SectionHelper {

    private static final String SET = "set";

    private final Map<String, Expression> parameters;

    SetSectionHelper(Map<String, Expression> parameters) {
        this.parameters = parameters;
    }

    @Override
    public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
        CompletableFuture<ResultNode> result = new CompletableFuture<>();

        @SuppressWarnings("unchecked")
        CompletableFuture<Object>[] paramResults = new CompletableFuture[parameters.size()];
        int idx = 0;
        for (Entry<String, Expression> entry : parameters.entrySet()) {
            paramResults[idx++] = context.resolutionContext().evaluate(entry.getValue()).toCompletableFuture();
        }
        CompletableFuture.allOf(paramResults).whenComplete((v, t1) -> {
            if (t1 != null) {
                result.completeExceptionally(t1);
            } else {
                // Build a map from the params
                Map<String, Object> paramValues = new HashMap<>();
                int j = 0;
                try {
                    for (Entry<String, Expression> entry : parameters.entrySet()) {
                        paramValues.put(entry.getKey(), paramResults[j++].get());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw new IllegalStateException(e);
                }
                context.execute(context.resolutionContext().createChild(paramValues, null)).whenComplete((r, t2) -> {
                    if (t2 != null) {
                        result.completeExceptionally(t2);
                    } else {
                        result.complete(r);
                    }
                });
            }
        });
        return result;
    }

    public static class Factory implements SectionHelperFactory<SetSectionHelper> {

        @Override
        public List<String> getDefaultAliases() {
            return ImmutableList.of(SET);
        }

        @Override
        public ParametersInfo getParameters() {
            return ParametersInfo.EMPTY;
        }

        @Override
        public SetSectionHelper initialize(SectionInitContext context) {
            Map<String, Expression> params = context.getParameters().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> Expression.parse(e.getValue())));
            return new SetSectionHelper(params);
        }

    }
}