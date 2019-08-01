package com.github.mkouba.qute.quarkus.example;

import java.math.BigDecimal;

import com.github.mkouba.qute.TemplateData;

@TemplateData
public class Item {

    private String name;

    private BigDecimal price;

    public Item(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

}
