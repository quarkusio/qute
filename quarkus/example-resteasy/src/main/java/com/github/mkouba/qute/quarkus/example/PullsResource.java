package com.github.mkouba.qute.quarkus.example;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.github.mkouba.qute.Engine;
import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.quarkus.TemplatePath;

@Path("/")
public class PullsResource {

    @TemplatePath
    Template pulls;

    @Inject
    Engine engine;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("pulls")
    public CompletionStage<String> getPulls() {
        Map<String, Object> data = new HashMap<>();
        data.put("generatedTime", LocalDateTime.now());
        StringBuilder buf = new StringBuilder();
        return pulls.render(data, part -> buf.append(part))
                .thenApply(v -> buf.toString());
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("onthefly")
    public String onTheFly() {
        return engine.parse("{this}").render("foo!");
    }

}
