package com.github.mkouba.qute.quarkus.example;

import static io.vertx.core.http.HttpMethod.GET;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.enterprise.event.Observes;

import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.quarkus.TemplatePath;

import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;
import io.vertx.ext.web.Router;

public class SimpleResource {

    @TemplatePath("simple.html")
    Template simple;

    @Route(path = "/simple", methods = GET, produces = "text/html")
    public void simple(RoutingExchange exchange) {
        exchange.ok(simple.render(Collections.singletonList("foo")));
    }

    void addErrorHandler(@Observes Router router) {
        router.errorHandler(500, c -> {
            c.response()
                    .end(c.failure().toString() + "\n" + Arrays.stream(c.failure().getStackTrace()).map(st -> st.toString())
                            .collect(Collectors.joining("\n\t")));
        });
    }

}
