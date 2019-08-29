package io.quarkus.qute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.qute.Engine;
import io.quarkus.qute.IfSectionHelper;
import io.quarkus.qute.LoopSectionHelper;
import io.quarkus.qute.Template;

public class LoopSectionTest {

    @Test
    public void tesLoop() {

        Map<String, String> item = new HashMap<>();
        item.put("name", "Lu");

        List<Map<String, String>> listOfMaps = new ArrayList<>();
        listOfMaps.add(item);
        listOfMaps.add(new HashMap<>());

        Engine engine = Engine.builder()
                .addSectionHelper(new IfSectionHelper.Factory())
                .addSectionHelper(new LoopSectionHelper.Factory()).addDefaultValueResolvers()
                .build();

        Template template = engine.parse("{#for item in this}{iter:count}.{name}={item:name}{#if iter:hasNext}\n{/if}{/for}");
        assertEquals("1.Lu=Lu\n2.NOT_FOUND=NOT_FOUND", template.render(listOfMaps));

        template = engine.parse("{#each this}{iter:count}.{name}={name}{#if iter:hasNext}\n{/if}{/each}");
        assertEquals("1.Lu=Lu\n2.NOT_FOUND=NOT_FOUND",
                template.render(listOfMaps));

        template = engine.parse("{#each this}{#if iter:odd}odd{:else}even{/if}{/each}");
        assertEquals("oddeven",
                template.render(listOfMaps));
    }

    @Test
    public void testMapEntrySet() {
        Map<String, String> map = new HashMap<>();
        map.put("name", "Lu");

        Engine engine = Engine.builder()
                .addSectionHelper(new LoopSectionHelper.Factory()).addDefaultValueResolvers()
                .build();

        assertEquals("name:Lu", engine.parse("{#each this}{key}:{value}{/each}").render(map));
    }

    @Test
    public void testStream() {
        List<String> data = new ArrayList<>();
        data.add("alpha");
        data.add("bravo");
        data.add("charlie");

        Engine engine = Engine.builder()
                .addSectionHelper(new LoopSectionHelper.Factory()).addDefaultValueResolvers()
                .build();

        assertEquals("alpha:charlie:",
                engine.parse("{#each this}{this}:{/each}").render(data.stream().filter(e -> !e.startsWith("b"))));
    }

}
