package com.github.mkouba.qute.quarkus.example;

import static io.vertx.core.http.HttpMethod.GET;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import com.github.mkouba.qute.Template;
import com.github.mkouba.qute.TemplateExtension;

import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;

public class ItemsResource {

    @Inject
    Template items;

    @TemplateExtension
    static BigDecimal discountedPrice(Item item) {
        return item.getPrice().multiply(new BigDecimal("0.9"));
    }

    @TemplateExtension
    static BigDecimal scaled(BigDecimal value, int newScale) {
        return value.setScale(newScale, RoundingMode.HALF_UP);
    }

    @TemplateExtension
    static String toUpperCase(String value) {
        return value.toUpperCase();
    }

    @Route(path = "/items", methods = GET, produces = "text/html")
    public void items(RoutingExchange exchange) {
        exchange.ok(items.render().putData("items", dummyItems()).getResult());
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
