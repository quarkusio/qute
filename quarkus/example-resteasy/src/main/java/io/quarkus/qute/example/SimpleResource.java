package io.quarkus.qute.example;

import java.util.Collections;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.qute.Template;

@Path("simple")
public class SimpleResource {

    @Inject
    Template simple;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String simple() {
        return simple.render(Collections.singletonList("foo"));
    }

}
