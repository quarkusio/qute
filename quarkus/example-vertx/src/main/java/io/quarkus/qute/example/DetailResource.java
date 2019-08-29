package io.quarkus.qute.example;

import static io.vertx.core.http.HttpMethod.GET;

import java.math.BigDecimal;

import javax.inject.Inject;

import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;

public class DetailResource {

    @Inject
    Template detail;
    
    @Route(path = "/item", methods = GET, produces = "text/html")
    public void item(RoutingExchange exchange) {
        exchange.ok(detail.render(new Item("Alpha", BigDecimal.valueOf(1000))));
    }

}
