package com.github.mkouba.qute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

public class SimpleTest {

    @Test
    public void testSimpleTemplate() {
        Map<String, String> item = new HashMap<>();
        item.put("name", "Lu");

        Map<String, Object> data = new HashMap<>();
        data.put("name", "world");
        data.put("test", Boolean.TRUE);
        data.put("list", ImmutableList.of(item));

        Engine engine = Engine.builder().addSectionHelper("if", new IfSectionHelper.Factory())
                .addSectionHelper("for", new LoopSectionHelper.Factory()).addValueResolver(ValueResolvers.mapResolver())
                .build();

        Template template = engine.parse("{#if test}Hello {name}!{/}\n\n{#for item in list}{item:name}{/}");
        assertEquals("Hello world!\n\nLu", template.render(data));
    }

    @Test
    public void tesCustomValueResolver() {
        Engine engine = Engine.builder().addValueResolver(ValueResolvers.thisResolver()).addValueResolver(new ValueResolver() {

            @Override
            public boolean appliesTo(EvalContext context) {
                return context.getBase() instanceof List && context.getName().equals("get") && context.getParams().size() == 1;
            }

            @Override
            public CompletionStage<Object> resolve(EvalContext context) {
                List<?> list = (List<?>) context.getBase();
                return CompletableFuture.completedFuture(list.get(Integer.valueOf(context.getParams().get(0))));
            }

        }).build();

        Template template = engine.parse("{this.get(0)}");
        assertEquals("moon", template.render(ImmutableList.of("moon")));
    }

    @Test
    public void testDataNamespace() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "world");
        data.put("test", Boolean.TRUE);

        Engine engine = Engine.builder().addSectionHelper("if", new IfSectionHelper.Factory())
                .addValueResolver(ValueResolvers.mapResolver())
                .build();

        Template template = engine.parse("{#if test}{data:name}{/if}");
        assertEquals("world", template.render(data));
    }

    @Test
    public void testOrElseResolver() {
        Engine engine = Engine.builder().addValueResolver(ValueResolvers.mapResolver())
                .addValueResolver(ValueResolvers.orResolver())
                .build();
        Map<String, Object> data = new HashMap<>();
        data.put("surname", "Bug");
        assertEquals("John Bug", engine.parse("{name.or('John')} {surname.or('John')}").render(data));
        assertEquals("John Bug", engine.parse("{name ?: 'John'} {surname or 'John'}").render(data));
        assertEquals("John Bug", engine.parse("{name ?: \"John Bug\"}").render(data));
    }

    @Test
    public void testMissingValue() {
        Engine engine = Engine.builder().addValueResolver(ValueResolvers.thisResolver())
                .addValueResolver(ValueResolvers.mapResolver())
                .addSectionHelper(new IfSectionHelper.Factory())
                .build();
        Map<String, Object> data = new HashMap<>();
        data.put("surname", "Bug");
        assertEquals("OK", engine.parse("{#if this.get('name') is null}OK{/}").render(data));
    }

    @Test
    public void testDelimitersEscaping() {
        assertEquals("{{foo}} bar",
                Engine.builder().addValueResolver(ValueResolvers.thisResolver()).build().parse("{{foo}} {this}").render("bar"));
    }

    @Test
    public void testComment() {
        assertEquals("OK",
                Engine.builder().build().parse("{! This is my comment}OK").render(null));
    }

}
