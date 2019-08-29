package io.quarkus.qute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.qute.Engine;
import io.quarkus.qute.LoopSectionHelper;
import io.quarkus.qute.ValueResolvers;

public class MapResolverTest {

    @Test
    public void tesMapResolver() {
        Map<String, String> map = new HashMap<>();
        map.put("name", "Lu");

        Engine engine = Engine.builder()
                .addSectionHelper(new LoopSectionHelper.Factory())
                .addValueResolver(ValueResolvers.thisResolver())
                .addValueResolver(ValueResolvers.mapResolver())
                .build();

        assertEquals("Lu,1,false,true,name",
                engine.parse("{this.name},{this.size},{this.empty},{this.containsKey('name')},{#each this.keys}{this}{/each}")
                        .render(map));
    }

}
