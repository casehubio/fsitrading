package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.ledger.TradingLedgerService;
import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.app.service.OrderService;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.Instrument;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TradeDecision;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuditResourceTest {

    @Inject
    TradingLedgerService tradingLedgerService;

    @Inject
    OrderService orderService;

    @Inject
    StrategyService strategyService;

    @Test
    void auditTrailReturnsEntriesForOrder() {
        var strategy = strategyService.create("audit-test", StrategyType.MOMENTUM);
        var decision = new TradeDecision(
                strategy.getId().toString(),
                new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ"),
                OrderSide.BUY, new BigDecimal("50"), OrderType.MARKET, null, "momentum signal");
        var order = orderService.createFromDecision(decision);
        order = orderService.fill(order.getId(), new BigDecimal("190.50"));

        var evalId = tradingLedgerService.recordStrategyEvaluation(
                order.getId(), strategy.getId(), "audit-test", StrategyType.MOMENTUM,
                "AAPL", "BUY", "momentum signal");
        tradingLedgerService.recordOrderExecution(order, evalId);

        given()
                .when().get("/api/audit/orders/" + order.getId())
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].digest", notNullValue())
                .body("[1].digest", notNullValue());
    }

    @Test
    void auditTrailContainsCausalityChain() {
        var strategy = strategyService.create("chain-test", StrategyType.MEAN_REVERSION);
        var decision = new TradeDecision(
                strategy.getId().toString(),
                new Instrument("MSFT", AssetClass.EQUITY, "NASDAQ"),
                OrderSide.SELL, new BigDecimal("25"), OrderType.LIMIT, new BigDecimal("420"), "RSI > 70");
        var order = orderService.createFromDecision(decision);
        order = orderService.fill(order.getId(), new BigDecimal("420.00"));

        var evalId = tradingLedgerService.recordStrategyEvaluation(
                order.getId(), strategy.getId(), "chain-test", StrategyType.MEAN_REVERSION,
                "MSFT", "SELL", "RSI > 70");
        tradingLedgerService.recordOrderExecution(order, evalId);

        given()
                .when().get("/api/audit/orders/" + order.getId())
                .then()
                .statusCode(200)
                .body("find { it.discriminator == 'STRATEGY_EVALUATION' }.causedByEntryId", nullValue())
                .body("find { it.discriminator == 'ORDER_EXECUTION' }.causedByEntryId", notNullValue())
                .body("find { it.discriminator == 'ORDER_EXECUTION' }.instrument", equalTo("MSFT"))
                .body("find { it.discriminator == 'ORDER_EXECUTION' }.side", equalTo("SELL"));
    }

    @Test
    void auditTrailEmptyForUnknownOrder() {
        given()
                .when().get("/api/audit/orders/" + UUID.randomUUID())
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }
}
