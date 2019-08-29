package io.quarkus.qute;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import io.quarkus.qute.SectionHelperFactory.SectionInitContext;

/**
 * Basic {@code if} statement.
 */
public class IfSectionHelper implements SectionHelper {

    private static final String CONDITION = "condition";
    private static final String OPERATOR = "operator";
    private static final String OPERAND = "operand";
    private static final String ELSE = "else";
    private static final String IF = "if";

    private final List<Block> blocks;

    IfSectionHelper(SectionInitContext context) {
        if (!context.hasParameter(CONDITION)) {
            throw new IllegalStateException("Condition param must be present");
        }
        ImmutableList.Builder<Block> builder = ImmutableList.builder();
        for (SectionBlock part : context.getBlocks()) {
            if ("main".equals(part.label) || ELSE.equals(part.label)) {
                builder.add(new Block(part));
            }
        }
        this.blocks = builder.build();
    }

    @Override
    public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
        return resolveCondition(context, blocks.iterator());
    }

    private CompletionStage<ResultNode> resolveCondition(SectionResolutionContext context,
            Iterator<Block> blocks) {
        Block block = blocks.next();
        if (block.condition == null) {
            // else without condition
            return context.execute(block.block, context.resolutionContext());
        }
        if (block.operator != null) {
            // If operator is used we need to compare the results of condition and operand
            CompletableFuture<ResultNode> result = new CompletableFuture<ResultNode>();
            CompletableFuture<?> cf1 = context.resolutionContext().evaluate(block.condition).toCompletableFuture();
            CompletableFuture<?> cf2 = context.resolutionContext().evaluate(block.operand).toCompletableFuture();
            CompletableFuture.allOf(cf1, cf2).whenComplete((v, t1) -> {
                if (t1 != null) {
                    result.completeExceptionally(t1);
                } else {
                    Object op1;
                    Object op2;
                    try {
                        op1 = cf1.get();
                        op2 = cf2.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new IllegalStateException(e);
                    }
                    try {
                        if (block.operator.evaluate(op1, op2)) {
                            context.execute(block.block, context.resolutionContext()).whenComplete((r, t2) -> {
                                if (t2 != null) {
                                    result.completeExceptionally(t2);
                                } else {
                                    result.complete(r);
                                }
                            });
                        } else {
                            if (blocks.hasNext()) {
                                resolveCondition(context, blocks).whenComplete((r, t2) -> {
                                    if (t2 != null) {
                                        result.completeExceptionally(t2);
                                    } else {
                                        result.complete(r);
                                    }
                                });
                            } else {
                                result.complete(ResultNode.NOOP);
                            }
                        }
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                }
            });
            return result;
        } else {
            return context.resolutionContext().evaluate(block.condition).thenCompose(r -> {
                if (Boolean.TRUE.equals(r)) {
                    return context.execute(block.block, context.resolutionContext());
                } else {
                    if (blocks.hasNext()) {
                        return resolveCondition(context, blocks);
                    }
                    return CompletableFuture.completedFuture(ResultNode.NOOP);
                }
            });
        }
    }

    public static class Factory implements SectionHelperFactory<IfSectionHelper> {

        @Override
        public List<String> getDefaultAliases() {
            return ImmutableList.of(IF);
        }

        @Override
        public ParametersInfo getParameters() {
            ParametersInfo.Builder builder = ParametersInfo.builder();
            // if params
            builder.addParameter(CONDITION);
            builder.addParameter(new Parameter(OPERATOR, null, true));
            builder.addParameter(new Parameter(OPERAND, null, true));
            // else parts
            // dummy "if" param first
            builder.addParameter(ELSE, new Parameter(IF, null, true));
            builder.addParameter(ELSE, new Parameter(CONDITION, null, true));
            builder.addParameter(ELSE, new Parameter(OPERATOR, null, true));
            builder.addParameter(ELSE, new Parameter(OPERAND, null, true));
            return builder
                    .build();
        }

        @Override
        public IfSectionHelper initialize(SectionInitContext context) {
            return new IfSectionHelper(context);
        }

    }

    static class Block {

        final SectionBlock block;
        final Expression condition;
        final Expression operand;
        final Operator operator;

        public Block(SectionBlock block) {
            this.block = block;
            String conditionParam = block.parameters.get(CONDITION);
            this.condition = conditionParam != null ? Expression.parse(conditionParam) : null;
            this.operator = Operator.from(block.parameters.get(OPERATOR));
            if (operator != null) {
                String operandParam = block.parameters.get(OPERAND);
                if (operandParam == null) {
                    throw new IllegalArgumentException("Operator set but no operand param present");
                }
                this.operand = Expression.parse(operandParam);
            } else {
                this.operand = null;
            }
        }

    }

    enum Operator {

        EQ("eq", "==", "is"),
        NE("ne", "!="),
        GT("gt", ">"),
        GE("ge", ">="),
        LE("le", "<="),
        LT("lt", "<"),
        ;

        private List<String> aliases;

        Operator(String... aliases) {
            this.aliases = Arrays.asList(aliases);
        }

        boolean evaluate(Object op1, Object op2) {
            // TODO better handling of Comparable, numbers, etc.
            switch (this) {
                case EQ:
                    return Objects.equals(op1, op2);
                case NE:
                    return !Objects.equals(op1, op2);
                case GE:
                    return getDecimal(op1).compareTo(getDecimal(op2)) >= 0;
                case GT:
                    return getDecimal(op1).compareTo(getDecimal(op2)) > 0;
                case LE:
                    return getDecimal(op1).compareTo(getDecimal(op2)) <= 0;
                case LT:
                    return getDecimal(op1).compareTo(getDecimal(op2)) < 0;
                default:
                    return false;
            }
        }

        static Operator from(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            for (Operator operator : values()) {
                if (operator.aliases.contains(value)) {
                    return operator;
                }
            }
            return null;
        }

    }

    static BigDecimal getDecimal(Object value) {
        BigDecimal decimal;
        if (value instanceof BigDecimal) {
            decimal = (BigDecimal) value;
        } else if (value instanceof BigInteger) {
            decimal = new BigDecimal((BigInteger) value);
        } else if (value instanceof Long) {
            decimal = new BigDecimal((Long) value);
        } else if (value instanceof Integer) {
            decimal = new BigDecimal((Integer) value);
        } else if (value instanceof Double) {
            decimal = new BigDecimal((Double) value);
        } else if (value instanceof String) {
            decimal = new BigDecimal(value.toString());
        } else {
            throw new IllegalArgumentException("Not a valid number: " + value);
        }
        return decimal;
    }

}