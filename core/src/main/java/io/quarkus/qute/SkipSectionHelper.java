package io.quarkus.qute;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Skips the content. May be used to comment out a large part of a template.
 */
public class SkipSectionHelper implements SectionHelper {

    private static final String NAME = "skip";

    @Override
    public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
        return CompletableFuture.completedFuture(ResultNode.NOOP);
    }

    public static class Factory implements SectionHelperFactory<SkipSectionHelper> {

        @Override
        public List<String> getDefaultAliases() {
            return ImmutableList.of(NAME);
        }

        @Override
        public ParametersInfo getParameters() {
            return ParametersInfo.EMPTY;
        }

        @Override
        public SkipSectionHelper initialize(SectionInitContext context) {
            return new SkipSectionHelper();
        }

    }
}