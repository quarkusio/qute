package io.quarkus.qute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.qute.Engine;
import io.quarkus.qute.LoopSectionHelper;
import io.quarkus.qute.ValueResolvers;

public class CollectionResolverTest {

    @Test
    public void testResolver() {
        List<String> list = new ArrayList<>();
        list.add("Lu");

        Engine engine = Engine.builder()
                .addSectionHelper(new LoopSectionHelper.Factory())
                .addValueResolver(ValueResolvers.thisResolver())
                .addValueResolver(ValueResolvers.collectionResolver())
                .build();

        assertEquals("1,false,true",
                engine.parse("{this.size},{this.isEmpty},{this.contains('Lu')}").render(list));
    }

}
