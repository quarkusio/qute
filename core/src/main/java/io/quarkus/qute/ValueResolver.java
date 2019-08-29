package io.quarkus.qute;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Value resolver.
 */
public interface ValueResolver extends Resolver {

    int DEFAULT_PRIORITY = 1;

    /**
     * @return the priority value
     */
    default int getPriority() {
        return DEFAULT_PRIORITY;
    }

    /**
     * 
     * @param context
     * @return {@code true} if this resolver applies to the given context
     */
    default boolean appliesTo(EvalContext context) {
        return true;
    }
    
    static <BASE> Builder<BASE> match(Class<BASE> baseClass) {
        return new Builder<>(baseClass);
    }

    class Builder<BASE> implements Supplier<ValueResolver> {

        private Predicate<EvalContext> appliesTo;
        private Function<EvalContext, CompletionStage<Object>> resolve;
        private int priority;

        public Builder(Class<BASE> match) {
            this.appliesTo = new Predicate<EvalContext>() {
                @Override
                public boolean test(EvalContext ctx) {
                    return ctx.getBase() != null && match.isAssignableFrom(ctx.getBase().getClass());
                }
            };
            this.priority = DEFAULT_PRIORITY;
        }

        public Builder<BASE> andMatch(String name) {
            return andAppliesTo(new Predicate<EvalContext>() {
                @Override
                public boolean test(EvalContext ctx) {
                    return ctx.getName().equals(name);
                }
            });
        }

        public Builder<BASE> andAppliesTo(Predicate<EvalContext> predicate) {
            this.appliesTo = this.appliesTo.and(predicate);
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder<BASE> resolve(BiFunction<BASE, String, Object> func) {
            this.resolve = ctx -> {
                BASE object = (BASE) ctx.getBase();
                return CompletableFuture.completedFuture(func.apply(object, ctx.getName()));
            };
            return this;
        }

        public Builder<BASE> resolve(Function<EvalContext, Object> func) {
            this.resolve = ctx -> CompletableFuture.completedFuture(func.apply(ctx));
            return this;
        }

        public Builder<BASE> resolveAsync(Function<EvalContext, CompletionStage<Object>> func) {
            this.resolve = func;
            return this;
        }

        public Builder<BASE> priority(int priority) {
            this.priority = priority;
            return this;
        }

        public ValueResolver build() {
            Objects.requireNonNull(appliesTo);
            Objects.requireNonNull(resolve);
            return new ValueResolver() {

                @Override
                public CompletionStage<Object> resolve(EvalContext context) {
                    return resolve.apply(context);
                }

                @Override
                public int getPriority() {
                    return priority;
                }

                @Override
                public boolean appliesTo(EvalContext context) {
                    return appliesTo.test(context);
                }
            };
        }

        @Override
        public ValueResolver get() {
            return build();
        }

    }

}