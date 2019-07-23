package com.github.mkouba.qute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class UserTagSectionHelper implements SectionHelper {

    private static final String IT = "it";

    private final Supplier<Template> templateSupplier;
    private final Map<String, Expression> parameters;

    public UserTagSectionHelper(Supplier<Template> templateSupplier, Map<String, Expression> parameters) {
        this.templateSupplier = templateSupplier;
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
                ((TemplateImpl) templateSupplier.get()).root.resolve(context.resolutionContext().createChild(paramValues, null))
                        .whenComplete((resultNode, t2) -> {
                            if (t2 != null) {
                                result.completeExceptionally(t2);
                            } else {
                                result.complete(resultNode);
                            }
                        });
            }
        });
        return result;
    }

    public static class Factory implements SectionHelperFactory<UserTagSectionHelper> {

        private final String name;

        public Factory(String name) {
            this.name = name;
        }

        @Override
        public List<String> getDefaultAliases() {
            return ImmutableList.of(name);
        }

        @Override
        public ParametersInfo getParameters() {
            return ParametersInfo.builder().addParameter(new Parameter(IT, null, true)).build();
        }

        @Override
        public UserTagSectionHelper initialize(SectionInitContext context) {

            Map<String, Expression> params = context.getParameters().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> Expression.parse(e.getValue())));

            return new UserTagSectionHelper(new Supplier<Template>() {

                @Override
                public Template get() {
                    Template template = context.getEngine().getTemplate(name);
                    if (template == null) {
                        throw new IllegalStateException("Template not found: " + name);
                    }
                    return template;
                }
            }, params);
        }

    }

}
