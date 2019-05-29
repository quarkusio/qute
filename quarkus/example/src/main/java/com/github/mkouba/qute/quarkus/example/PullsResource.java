package com.github.mkouba.qute.quarkus.example;

import static io.vertx.core.http.HttpMethod.GET;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.github.mkouba.qute.Engine;
import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.quarkus.TemplatePath;

import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;

public class PullsResource {

    @TemplatePath("pulls.html")
    Template pulls;

    @Inject
    Engine engine;

    @Route(path = "/pulls", methods = GET, produces = "text/html")
    public void getPulls(RoutingExchange exchange) {
        exchange.response().setChunked(true);
        Map<String, Object> data = new HashMap<>();
        data.put("generatedTime", LocalDateTime.now());
        pulls.render(data, exchange.response()::write)
                .whenComplete((v, t) -> {
                    if (t == null) {
                        exchange.ok().end();
                    } else {
                        Throwable cause = t.getCause();
                        exchange.serverError()
                                .end(cause.toString() + "\n"
                                        + Arrays.stream(cause.getStackTrace()).map(s -> "\t" + s.toString())
                                                .collect(Collectors.joining("\n")));
                    }
                });
    }

    @Route(path = "/onthefly", methods = GET, produces = "text/html")
    public void onTheFly(RoutingExchange exchange) {
        exchange.ok(engine.parse("{this}").render("foo!"));
    }

}
