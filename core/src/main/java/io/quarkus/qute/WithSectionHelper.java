package io.quarkus.qute;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkus.qute.SectionHelperFactory.SectionInitContext;

/**
 * Basic {@code with} statement.
 */
public class WithSectionHelper implements SectionHelper {

    private static final String OBJECT = "object";
    private static final String WITH = "with";
    private static final String AS = "as";
    private static final String ALIAS = "alias";

    private final Expression object;
    private final SectionBlock main;
    private final String alias;

    WithSectionHelper(SectionInitContext context) {
        if (!context.hasParameter(OBJECT)) {
            throw new IllegalStateException("Object param not present");
        }
        this.object = Expression.parse(context.getParameter(OBJECT));
        this.alias = context.getParameter(ALIAS);
        this.main = context.getBlocks().get(0);
    }

    @Override
    public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
        return context.resolutionContext().evaluate(object)
                .thenCompose(with -> {
                    AtomicReference<ResolutionContext> resolutionContextHolder = new AtomicReference<>();
                    List<NamespaceResolver> namespaceResolvers = alias != null
                            ? Collections.singletonList(new AliasResolver(alias, resolutionContextHolder))
                            : null;
                    ResolutionContext child = context.resolutionContext().createChild(with, namespaceResolvers);
                    resolutionContextHolder.set(child);
                    return context.execute(main, child);
                });
    }

    public static class Factory implements SectionHelperFactory<WithSectionHelper> {

        @Override
        public List<String> getDefaultAliases() {
            return ImmutableList.of(WITH);
        }

        @Override
        public ParametersInfo getParameters() {
            return ParametersInfo.builder().addParameter(OBJECT).addParameter(AS, Parameter.EMPTY)
                    .addParameter(new Parameter(ALIAS, null, true)).build();
        }

        @Override
        public WithSectionHelper initialize(SectionInitContext context) {
            return new WithSectionHelper(context);
        }

    }
}