package io.quarkus.qute;

import static io.quarkus.qute.Futures.evaluateParams;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
        evaluateParams(parameters, context.resolutionContext()).whenComplete((r, t) -> {
            if (t != null) {
                result.completeExceptionally(t);
            } else {
                // Execute the main block with the params as the current context object
                context.execute(context.resolutionContext().createChild(r, null)).whenComplete((r2, t2) -> {
                    if (t2 != null) {
                        result.completeExceptionally(t2);
                    } else {
                        result.complete(r2);
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