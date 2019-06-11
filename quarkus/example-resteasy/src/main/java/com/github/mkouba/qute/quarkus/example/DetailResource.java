package com.github.mkouba.qute.quarkus.example;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.quarkus.TemplatePath;
import com.github.mkouba.qute.quarkus.example.api.MailInstance;
import com.github.mkouba.qute.quarkus.example.api.TemplateInstance;

@Path("/")
public class DetailResource {

    @TemplatePath
    Template detail;

    @Path("item")
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String item() {
        return detail.render(new Item("Alpha", BigDecimal.valueOf(1000)));
    }

    @Path("item2")
    @GET
    @Produces({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN})
    public TemplateInstance item2() {
        return new TemplateInstance(new Item("Alpha", BigDecimal.valueOf(1000)));
    }

    @Path("item-mail")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public CompletionStage<String> itemMail() {
        // WE NEED A MOCK MAILER
        return new MailInstance(new Item("Alpha", BigDecimal.valueOf(1000)))
                .subject("TEST")
                .from("stef@epardaud.fr")
                .to("stef@epardaud.fr")
                .send().thenApply(v -> "OK");
    }
}
