package com.github.mkouba.qute.generator;

import com.github.mkouba.qute.TemplateData;

@TemplateData(properties = true)
public class MyItem {

    public String id = "foo";

    public String getBar(int limit) {
        return "bar";
    }

}
