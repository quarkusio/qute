package io.quarkus.qute.example;

import java.time.LocalDateTime;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;

@Path("/")
public class PullsResource {

    @Inject
    Template pulls;

    @Inject
    Engine engine;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("pulls")
    public CompletionStage<String> getPulls() {
        StringBuilder buf = new StringBuilder();
        return pulls.render().putData("generatedTime", LocalDateTime.now()).consume(part -> buf.append(part))
                .thenApply(v -> buf.toString());
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("onthefly")
    public String onTheFly() {
        return engine.parse("{this}").render("foo!");
    }

}
