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
class MarketDataResourceTest {

    @Test
    @Order(1)
    void generateTick_returnsPriceTick() {
        given()
                .when().post("/api/market-data/tick")
                .then()
                .statusCode(200)
                .body("instrument", notNullValue())
                .body("price", notNullValue());
    }

    @Test
    @Order(2)
    void bars_emptyInitially() {
        given()
                .when().get("/api/market-data/bars/AAPL")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(3)
    void trends_emptyInitially() {
        given()
                .when().get("/api/market-data/trends/AAPL")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(4)
    void regime_emptyInitially() {
        given()
                .when().get("/api/market-data/regime/AAPL")
                .then()
                .statusCode(anyOf(is(200), is(204)));
    }

    @Test
    @Order(5)
    void narrative_emptyInitially() {
        given()
                .when().get("/api/market-data/narrative")
                .then()
                .statusCode(anyOf(is(200), is(204)));
    }

    @Test
    @Order(6)
    void scenario_acceptsValidType() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"scenarioType\": \"NORMAL_DAY\"}")
                .when().post("/api/market-data/scenario")
                .then()
                .statusCode(200)
                .body("tickCount", greaterThan(0));
    }

    @Test
    @Order(7)
    void scheduler_pauseAndResume() {
        given()
                .when().post("/api/market-data/scheduler/pause")
                .then()
                .statusCode(204);

        given()
                .when().post("/api/market-data/scheduler/resume")
                .then()
                .statusCode(204);
    }
}
