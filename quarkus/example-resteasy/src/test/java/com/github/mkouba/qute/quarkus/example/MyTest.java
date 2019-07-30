package com.github.mkouba.qute.quarkus.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MyTest {
    public static final String HTML_BODY = "<!DOCTYPE html>\n" + 
            "<html>\n" + 
            "<head>\n" + 
            "<meta charset=\"UTF-8\">\n" + 
            "<style type=\"text/css\">\n" + 
            "body {  font-family: sans-serif;\n" + 
            "}\n" + 
            "</style>\n" + 
            "<title>Item Detail</title>\n" + 
            "</head>\n" + 
            "\n" + 
            "<body>\n" + 
            "    <h1>Item Detail - Alpha</h1>\n" + 
            "    \n" + 
            "    <div>\n" + 
            "        Price: 1000\n" + 
            "    </div>\n" + 
            "    <div>\n" + 
            "        Discounted Price: 900.0\n" + 
            "    </div>\n" + 
            "\n" + 
            "</body>\n" + 
            "\n" + 
            "</html>";
    
    public static final String TEXT_BODY = "Item Detail - Alpha\n" + 
            "--------------------\n" + 
            "\n" + 
            "Price: 1000\n" + 
            "Discounted Price: 900.0"; 
    
    @Test
    public void test() {
        given()
        .when()
        .get("/item2")
        .then()
           .statusCode(200)    
           .body(is(HTML_BODY));
        
        given()
        .when()
        .accept("text/plain")
        .get("/item2")
        .then()
           .statusCode(200)    
           .body(is(TEXT_BODY));
        
        given()
        .when()
        .get("/item3")
        .then()
           .statusCode(200)    
           .body(is(HTML_BODY));
        
        given()
        .when()
        .accept("text/plain")
        .get("/item3")
        .then()
           .statusCode(200)    
           .body(is(TEXT_BODY));
    }

    @Test
    public void testMail() {
        given()
        .when()
        .get("/item-mail")
        .then()
           .statusCode(200)    
           .body(is("OK"));
    }
}
