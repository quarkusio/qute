package io.quarkus.qute.deployment;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.qute.Expression;

public final class TemplatesAnalysisBuildItem extends SimpleBuildItem {

    private final List<TemplateAnalysis> analysis;

    public TemplatesAnalysisBuildItem(List<TemplateAnalysis> analysis) {
        this.analysis = analysis;
    }

    public List<TemplateAnalysis> getAnalysis() {
        return analysis;
    }

    static class TemplateAnalysis {

        public final String id;
        public final Set<Expression> expressions;
        public final Path path;

        public TemplateAnalysis(String id, Set<Expression> expressions, Path path) {
            this.id = id;
            this.expressions = expressions;
            this.path = path;
        }
    }

}
