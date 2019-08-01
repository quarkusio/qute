package com.github.mkouba.qute.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mkouba.qute.Template.Rendering;

import io.quarkus.test.QuarkusUnitTest;

public class VariantTemplateTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SimpleBean.class)
                    .addAsResource(new StringAsset("{this}"), "META-INF/resources/templates/foo.txt")
                    .addAsResource(new StringAsset("<strong>{this}</strong>"), "META-INF/resources/templates/foo.html"));

    @Inject
    SimpleBean simpleBean;

    @Test
    public void testRendering() {
        Rendering rendering = simpleBean.foo.render().setData("bar");
        rendering.setAttribute(VariantTemplate.SELECTED_VARIANT, new Variant(null, "text/plain", null));
        assertEquals("bar", rendering.getResult());
        rendering.setAttribute(VariantTemplate.SELECTED_VARIANT, new Variant(null, "text/html", null));
        assertEquals("<strong>bar</strong>", rendering.getResult());
    }

    @Dependent
    public static class SimpleBean {

        @Inject
        VariantTemplate foo;

    }

}
