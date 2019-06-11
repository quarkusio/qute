package com.github.mkouba.qute.quarkus.example;

import java.util.Collections;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.quarkus.TemplatePath;

@Path("simple")
public class SimpleResource {

    @TemplatePath
    Template simple;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String simple() {
        return simple.render(Collections.singletonList("foo"));
    }

}
