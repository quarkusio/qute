package io.quarkus.qute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Results;
import io.quarkus.qute.Template;
import io.quarkus.qute.ValueResolvers;
import io.quarkus.qute.WithSectionHelper;

public class WithSectionTest {

    @Test
    public void testWith() {
        Engine engine = Engine.builder().addSectionHelper(new WithSectionHelper.Factory())
                .addValueResolver(ValueResolvers.mapResolver())
                .build();

        Template template = engine.parse("{#with map}{key}{/with}");
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("key", "val");
        data.put("map", map);
        assertEquals("val", template.render(data));
    }

    @Test
    public void testAlias() {
        Engine engine = Engine.builder().addSectionHelper(new WithSectionHelper.Factory())
                .addValueResolver(ValueResolvers.mapResolver())
                .addValueResolver(v -> v.getBase().getClass().equals(String.class) && v.getName().equals("length")
                        ? CompletableFuture.completedFuture(3)
                        : Results.NOT_FOUND)
                .build();

        Template template = engine
                .parse("{#with map as myMap}{myMap:key} {#with key as myKey}{myMap:key}={myKey:length}{/with}{/with}");
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("key", "val");
        data.put("map", map);
        assertEquals("val val=3", template.render(data));
    }
}
