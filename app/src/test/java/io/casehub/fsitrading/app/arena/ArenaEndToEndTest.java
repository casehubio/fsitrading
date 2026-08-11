package io.casehub.fsitrading.app.arena;

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
class ArenaEndToEndTest {

    @Test
    @Order(1)
    void createStrategies() {
        for (String type : new String[]{"MOMENTUM", "EVENT_DRIVEN", "MARKET_MAKING"}) {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"name\": \"arena-" + type.toLowerCase() + "\", \"strategyType\": \"" + type + "\"}")
                    .when().post("/api/strategies")
                    .then()
                    .statusCode(200)
                    .body("active", equalTo(true));
        }
    }

    @Test
    @Order(2)
    void triggerArena_fullFlow() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"instrument\":\"AAPL\",\"eventType\":\"PRICE_MOVEMENT\",\"price\":185.50,\"volume\":10000}")
                .when().post("/api/evaluations/trigger")
                .then()
                .statusCode(200)
                .body("runId", notNullValue())
                .body("marketSignal.instrument", equalTo("AAPL"));
    }

    @Test
    @Order(3)
    void verifyKpisEndpoint() {
        given()
                .when().get("/api/kpis")
                .then()
                .statusCode(200)
                .body("tradeCount", notNullValue());
    }

    @Test
    @Order(4)
    void verifyRoutingDecisionsEndpoint() {
        given()
                .when().get("/api/routing/decisions")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(5)
    void verifyPreferencesEndpoint() {
        given()
                .when().get("/api/preferences/trust-routing")
                .then()
                .statusCode(200)
                .body("routingThreshold", notNullValue())
                .body("approvalTimeoutHours", notNullValue());
    }
}
