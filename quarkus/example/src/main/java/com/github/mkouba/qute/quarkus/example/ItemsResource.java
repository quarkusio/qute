package com.github.mkouba.qute.quarkus.example;

import static com.github.mkouba.qute.ValueResolver.match;
import static io.vertx.core.http.HttpMethod.GET;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.event.Observes;

import com.github.mkouba.qute.EngineBuilder;
import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.quarkus.TemplatePath;

import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;

public class ItemsResource {

    @TemplatePath
    Template items;

    void addResolver(@Observes EngineBuilder builder) {
        builder.addValueResolver(
                match(Item.class).andMatch("discountedPrice").resolve((i, n) -> i.getPrice().multiply(new BigDecimal("0.9"))));
        builder.addValueResolver(
                match(BigDecimal.class).andMatch("scaled").resolve((bd, n) -> bd.setScale(0, RoundingMode.HALF_UP)));

    }

    @Route(path = "/items", methods = GET, produces = "text/html")
    public void items(RoutingExchange exchange) {

        Map<String, Object> data = new HashMap<>();
        data.put("items", dummyItems());
        data.put("limit", BigDecimal.valueOf(800));
        exchange.ok(items.render(data));
    }

    private List<Item> dummyItems() {
        List<Item> items = new ArrayList<>();
        items.add(new Item("Alpha", BigDecimal.valueOf(1000)));
        items.add(new Item("Bravo", BigDecimal.valueOf(900)));
        items.add(new Item(null, BigDecimal.valueOf(650)));
        items.add(new Item("Delta", BigDecimal.valueOf(10)));
        return items;
    }

}
