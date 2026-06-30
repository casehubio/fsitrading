package io.casehub.fsitrading.app.ledger;

import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.app.service.OrderService;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.Instrument;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.fsitrading.model.TradeDecision;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TradingLedgerServiceTest {

    @Inject
    TradingLedgerService ledgerService;

    @Inject
    OrderService orderService;

    @Inject
    StrategyService strategyService;

    @Test
    void recordStrategyEvaluationCreatesEntry() {
        var orderId = UUID.randomUUID();
        var strategyId = UUID.randomUUID();
        var entryId = ledgerService.recordStrategyEvaluation(
                orderId, strategyId, "momentum-v1", StrategyType.MOMENTUM,
                "AAPL", "BUY", "Price crossed SMA");

        assertNotNull(entryId);
        var entries = ledgerService.findByOrderId(orderId);
        assertEquals(1, entries.size());

        var entry = entries.get(0);
        assertInstanceOf(StrategyEvaluationLedgerEntry.class, entry);
        var eval = (StrategyEvaluationLedgerEntry) entry;
        assertEquals(strategyId, eval.strategyId);
        assertEquals("AAPL", eval.instrument);
        assertEquals("BUY", eval.signal);
        assertEquals(FsiActorIdentity.forStrategy(StrategyType.MOMENTUM), eval.actorId);
    }

    @Test
    void recordOrderExecutionCreatesEntry() {
        var strategy = strategyService.create("test-strat", StrategyType.MOMENTUM);
        var decision = new TradeDecision(
                strategy.getId().toString(),
                new Instrument("MSFT", AssetClass.EQUITY, "NASDAQ"),
                OrderSide.BUY, new BigDecimal("100"), OrderType.MARKET, null, "test");
        var order = orderService.createFromDecision(decision);
        order = orderService.fill(order.getId(), new BigDecimal("420.50"));

        var entryId = ledgerService.recordOrderExecution(order, null);

        assertNotNull(entryId);
        var entries = ledgerService.findByOrderId(order.getId());
        assertEquals(1, entries.size());

        var entry = entries.get(0);
        assertInstanceOf(OrderExecutionLedgerEntry.class, entry);
        var exec = (OrderExecutionLedgerEntry) entry;
        assertEquals(order.getId(), exec.orderId);
        assertEquals("MSFT", exec.instrument);
        assertEquals("BUY", exec.side);
        assertEquals(0, new BigDecimal("100").compareTo(exec.quantity));
        assertEquals(0, new BigDecimal("420.50").compareTo(exec.fillPrice));
    }

    @Test
    void causalityChainLinks() {
        var strategy = strategyService.create("chain-strat", StrategyType.MEAN_REVERSION);
        var decision = new TradeDecision(
                strategy.getId().toString(),
                new Instrument("GOOGL", AssetClass.EQUITY, "NASDAQ"),
                OrderSide.SELL, new BigDecimal("50"), OrderType.LIMIT, new BigDecimal("175.00"), "RSI high");
        var order = orderService.createFromDecision(decision);
        order = orderService.fill(order.getId(), new BigDecimal("175.00"));

        var evalId = ledgerService.recordStrategyEvaluation(
                order.getId(), strategy.getId(), "mean-reversion-v1", StrategyType.MEAN_REVERSION,
                "GOOGL", "SELL", "RSI > 70");
        var execId = ledgerService.recordOrderExecution(order, evalId);

        var entries = ledgerService.findByOrderId(order.getId());
        assertEquals(2, entries.size());

        var eval = entries.stream()
                .filter(StrategyEvaluationLedgerEntry.class::isInstance)
                .findFirst().orElseThrow();
        var exec = entries.stream()
                .filter(OrderExecutionLedgerEntry.class::isInstance)
                .findFirst().orElseThrow();

        assertNull(eval.causedByEntryId);
        assertEquals(eval.id, exec.causedByEntryId);
    }

    @Test
    void digestPopulatedOnSave() {
        var orderId = UUID.randomUUID();
        ledgerService.recordStrategyEvaluation(
                orderId, UUID.randomUUID(), "test", StrategyType.MOMENTUM,
                "NVDA", "HOLD", "No signal");

        var entries = ledgerService.findByOrderId(orderId);
        assertFalse(entries.isEmpty());
        assertNotNull(entries.get(0).digest, "Merkle leaf hash should be computed");
        assertFalse(entries.get(0).digest.isBlank());
    }

    @Test
    void sequenceNumbersContiguous() {
        var strategy = strategyService.create("seq-strat", StrategyType.MOMENTUM);
        var decision = new TradeDecision(
                strategy.getId().toString(),
                new Instrument("AMZN", AssetClass.EQUITY, "NASDAQ"),
                OrderSide.BUY, new BigDecimal("10"), OrderType.MARKET, null, "momentum");
        var order = orderService.createFromDecision(decision);
        order = orderService.fill(order.getId(), new BigDecimal("185.00"));

        ledgerService.recordStrategyEvaluation(
                order.getId(), strategy.getId(), "momentum-v1", StrategyType.MOMENTUM,
                "AMZN", "BUY", "breakout");
        ledgerService.recordOrderExecution(order, null);

        var entries = ledgerService.findByOrderId(order.getId());
        assertEquals(2, entries.size());

        var seqNumbers = entries.stream().map(e -> e.sequenceNumber).sorted().toList();
        assertEquals(1, seqNumbers.get(0));
        assertEquals(2, seqNumbers.get(1));
    }
}
