package io.quarkus.qute.deployment;

import static org.junit.jupiter.api.Assertions.fail;

import javax.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class TypeSafeLoopFailureTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(Foo.class)
                    .addAsResource(new StringAsset("{@list=java.util.List<io.quarkus.qute.deployment.Foo>}"
                            + "{#for foo in list}"
                            + "{foo.name}={foo.ages}"
                            + "{/}"), "META-INF/resources/templates/foo.html"))
            .setExpectedException(DeploymentException.class);

    @Test
    public void testValidation() {
        fail();
    }

}
