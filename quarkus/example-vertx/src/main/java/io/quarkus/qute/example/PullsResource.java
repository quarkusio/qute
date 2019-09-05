package io.quarkus.qute.example;

import static io.vertx.core.http.HttpMethod.GET;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.api.ResourcePath;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;

public class PullsResource {

    @ResourcePath("github/pulls")
    Template pulls;

    @Inject
    Engine engine;

    @Route(path = "/pulls", methods = GET, produces = "text/html")
    public void getPulls(RoutingExchange exchange) {
        long start = System.currentTimeMillis();
        exchange.response().setChunked(true);
        pulls.render().putData("generatedTime", LocalDateTime.now()).consume(exchange.response()::write)
                .whenComplete((v, t) -> {
                    if (t == null) {
                        exchange.ok().end();
                        System.out.println("Rendered in " + (System.currentTimeMillis() - start) + " ms");
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
