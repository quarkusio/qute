package io.quarkus.qute.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateExtension;

@Path("items")
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

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String items() {
        return items.render().putData("items", dummyItems()).putData("limit", BigDecimal.valueOf(800)).getResult();
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
