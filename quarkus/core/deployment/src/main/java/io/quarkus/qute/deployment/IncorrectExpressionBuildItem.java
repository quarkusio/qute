package io.quarkus.qute.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class IncorrectExpressionBuildItem extends MultiBuildItem {

    public final String expression;
    public final String property;
    public final String clazz;

    public IncorrectExpressionBuildItem(String expression, String property, String clazz) {
        this.expression = expression;
        this.property = property;
        this.clazz = clazz;
    }

}
