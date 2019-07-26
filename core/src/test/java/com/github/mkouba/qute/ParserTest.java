package com.github.mkouba.qute;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

public class ParserTest {

    @Test
    public void testSectionEndValidation() {
        Engine engine = Engine.builder().addDefaultSectionHelpers()
                .build();
        try {
            engine.parse("{#if test}Hello {name}!{/for}");
            fail();
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();
            assertTrue(message.contains("if"));
            assertTrue(message.contains("for"));
        }
    }

}
