package io.casehub.fsitrading.app.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TrustScoreResourceTest {

    @Test
    void listAllReturnsAllStrategyTypes() {
        given()
                .when().get("/api/trust/strategies")
                .then()
                .statusCode(200)
                .body("size()", equalTo(7))
                .body("strategyType", hasItems("MOMENTUM", "MEAN_REVERSION", "STATISTICAL_ARBITRAGE"));
    }

    @Test
    void listAllShowsBootstrapPhaseWhenNoAttestations() {
        given()
                .when().get("/api/trust/strategies")
                .then()
                .statusCode(200)
                .body("[0].phase", equalTo("BOOTSTRAP"))
                .body("[0].trustScore", nullValue())
                .body("[0].decisionCount", equalTo(0));
    }

    @Test
    void getByTypeReturnsSpecificStrategy() {
        given()
                .when().get("/api/trust/strategies/MOMENTUM")
                .then()
                .statusCode(200)
                .body("strategyType", equalTo("MOMENTUM"))
                .body("actorId", equalTo("rule:momentum@v1"))
                .body("phase", equalTo("BOOTSTRAP"));
    }

    @Test
    void getByTypeCaseInsensitive() {
        given()
                .when().get("/api/trust/strategies/momentum")
                .then()
                .statusCode(200)
                .body("strategyType", equalTo("MOMENTUM"));
    }

    @Test
    void getByTypeReturns404ForUnknown() {
        given()
                .when().get("/api/trust/strategies/NONEXISTENT")
                .then()
                .statusCode(404);
    }

    @Test
    void attestationSummaryPresentAndZeroWhenNoAttestations() {
        given()
                .when().get("/api/trust/strategies/MOMENTUM")
                .then()
                .statusCode(200)
                .body("attestationSummary.positive", equalTo(0))
                .body("attestationSummary.negative", equalTo(0));
    }
}
