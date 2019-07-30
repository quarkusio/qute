package com.github.mkouba.qute;

import static com.github.mkouba.qute.Futures.evaluateParams;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
        evaluateParams(parameters, context.resolutionContext()).whenComplete((r, t) -> {
            if (t != null) {
                result.completeExceptionally(t);
            } else {
                // Execute the template with the params as the root context object
                ((TemplateImpl) templateSupplier.get()).root.resolve(context.resolutionContext().createChild(r, null))
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
            return ParametersInfo.builder().addParameter(new Parameter(IT, "this", true)).build();
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
