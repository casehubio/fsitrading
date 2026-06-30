package io.casehub.fsitrading.app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TradingEndToEndTest {

    private static String strategyId;

    @Test
    @Order(1)
    void createStrategy() {
        strategyId = given()
                .contentType(ContentType.JSON)
                .body("{\"name\": \"test-momentum\", \"strategyType\": \"MOMENTUM\"}")
                .when().post("/api/strategies")
                .then()
                .statusCode(200)
                .body("name", equalTo("test-momentum"))
                .body("strategyType", equalTo("MOMENTUM"))
                .body("active", equalTo(true))
                .extract().path("id");
    }

    @Test
    @Order(2)
    void listStrategies() {
        given()
                .when().get("/api/strategies")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(3)
    void generateMarketTick() {
        given()
                .when().post("/api/market-data/tick")
                .then()
                .statusCode(200)
                .body("instrument", notNullValue())
                .body("price", notNullValue())
                .body("eventType", equalTo("PRICE_TICK"));
    }

    @Test
    @Order(4)
    void getRecentMarketData() {
        given()
                .queryParam("limit", 5)
                .when().get("/api/market-data/recent")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(5)
    void positionsStartEmpty() {
        given()
                .when().get("/api/positions")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(6)
    void ordersStartEmpty() {
        given()
                .when().get("/api/orders")
                .then()
                .statusCode(200);
    }
}
