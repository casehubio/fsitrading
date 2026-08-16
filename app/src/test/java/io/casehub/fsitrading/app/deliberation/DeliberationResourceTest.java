package io.casehub.fsitrading.app.deliberation;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DeliberationResourceTest {

    @Test
    void listDeliberations() {
        given()
                .when().get("/api/deliberations")
                .then()
                .statusCode(200);
    }

    @Test
    void getByIdReturns404ForUnknown() {
        given()
                .when().get("/api/deliberations/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void manualTriggerReturns202() {
        given()
                .queryParam("instrument", "DLBT")
                .when().post("/api/deliberations/trigger")
                .then()
                .statusCode(202);
    }

    @Test
    void manualTriggerReturns400WithoutInstrument() {
        given()
                .when().post("/api/deliberations/trigger")
                .then()
                .statusCode(400);
    }

    @Test
    void manualTriggerReturns409WhenInProgress() {
        var instrument = "DUP" + (System.currentTimeMillis() % 10000);

        given()
                .queryParam("instrument", instrument)
                .when().post("/api/deliberations/trigger")
                .then()
                .statusCode(202);

        given()
                .queryParam("instrument", instrument)
                .when().post("/api/deliberations/trigger")
                .then()
                .statusCode(409);
    }

    @Test
    void triggerThenGetById() {
        var instrument = "GET" + (System.currentTimeMillis() % 10000);

        var id = given()
                .queryParam("instrument", instrument)
                .when().post("/api/deliberations/trigger")
                .then()
                .statusCode(202)
                .extract().body().asString().replace("\"", "");

        given()
                .when().get("/api/deliberations/" + id)
                .then()
                .statusCode(200)
                .body("instrument", equalTo(instrument))
                .body("status", equalTo("IN_PROGRESS"));
    }
}
