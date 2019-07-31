package com.github.mkouba.qute.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

@ApplicationScoped
@Named
public class Hello {

    public String ping() {
        return "pong";
    }

}