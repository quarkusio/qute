package io.quarkus.qute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

import io.quarkus.qute.Engine;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.Results;

public class GlobalNamespaceResolverTest {

    @Test
    public void tesNamespaceResolver() {
        Engine engine = Engine.builder()
                .addNamespaceResolver(new NamespaceResolver() {

                    @Override
                    public CompletionStage<Object> resolve(EvalContext context) {
                        return context.getName().equals("foo") ? CompletableFuture.completedFuture("bar") : Results.NOT_FOUND;
                    }

                    @Override
                    public String getNamespace() {
                        return "global";
                    }
                })
                .build();

        assertEquals("bar", engine.parse("{global:foo}").render(null));
    }

}
