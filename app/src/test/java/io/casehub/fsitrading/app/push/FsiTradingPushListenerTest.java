package io.casehub.fsitrading.app.push;

import io.casehub.fsitrading.app.arena.RoutingDecisionEvent;
import io.casehub.fsitrading.app.arena.TrustScoreChangedEvent;
import io.casehub.fsitrading.app.service.PositionUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiTradingPushListenerTest {

    private final List<BroadcastCapture> broadcasts = new ArrayList<>();
    private FsiTradingPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new FsiTradingPushListener(
                (topic, event) -> broadcasts.add(new BroadcastCapture(topic, event)));
    }

    @Test
    void positionUpdated_broadcastsToPositionTopic() {
        var event = new PositionUpdatedEvent(
                UUID.randomUUID(), "AAPL", "EQUITY", UUID.randomUUID(),
                BigDecimal.TEN, BigDecimal.valueOf(150), null, null, null,
                Instant.now());

        listener.onPositionUpdated(event);

        assertEquals(1, broadcasts.size());
        assertEquals("position:AAPL", broadcasts.get(0).topic);
        assertInstanceOf(TradingPushPayload.PositionUpdate.class, broadcasts.get(0).event);
    }

    @Test
    void positionUpdated_withPnl_broadcastsToBothTopics() {
        var strategyId = UUID.randomUUID();
        var event = new PositionUpdatedEvent(
                UUID.randomUUID(), "AAPL", "EQUITY", strategyId,
                BigDecimal.TEN, BigDecimal.valueOf(150),
                BigDecimal.valueOf(500), BigDecimal.valueOf(155),
                BigDecimal.valueOf(10), Instant.now());

        listener.onPositionUpdated(event);

        assertEquals(2, broadcasts.size());
        assertEquals("position:AAPL", broadcasts.get(0).topic);
        assertEquals("pnl:" + strategyId, broadcasts.get(1).topic);
        assertInstanceOf(TradingPushPayload.PositionUpdate.class, broadcasts.get(0).event);
        assertInstanceOf(TradingPushPayload.PnlUpdate.class, broadcasts.get(1).event);
    }

    @Test
    void positionUpdated_noPnl_broadcastsOnlyPosition() {
        var event = new PositionUpdatedEvent(
                UUID.randomUUID(), "MSFT", "EQUITY", UUID.randomUUID(),
                BigDecimal.TEN, BigDecimal.valueOf(300),
                BigDecimal.valueOf(100), BigDecimal.valueOf(305), null,
                Instant.now());

        listener.onPositionUpdated(event);

        assertEquals(1, broadcasts.size());
        assertEquals("position:MSFT", broadcasts.get(0).topic);
    }

    @Test
    void positionUpdated_payloadCarriesAllFields() {
        var posId = UUID.randomUUID();
        var stratId = UUID.randomUUID();
        var now = Instant.now();
        var event = new PositionUpdatedEvent(
                posId, "GOOGL", "EQUITY", stratId,
                BigDecimal.valueOf(50), BigDecimal.valueOf(2800),
                null, null, null, now);

        listener.onPositionUpdated(event);

        var payload = (TradingPushPayload.PositionUpdate) broadcasts.get(0).event;
        assertEquals("POSITION_UPDATE", payload.type());
        assertEquals(posId, payload.positionId());
        assertEquals("GOOGL", payload.instrument());
        assertEquals("EQUITY", payload.assetClass());
        assertEquals(stratId, payload.strategyId());
        assertEquals(BigDecimal.valueOf(50), payload.quantity());
        assertEquals(BigDecimal.valueOf(2800), payload.avgCost());
        assertNull(payload.realizedPnl());
        assertEquals(now, payload.updatedAt());
    }

    @Test
    void trustChanged_broadcastsToTrustTopic() {
        var event = new TrustScoreChangedEvent(
                "MOMENTUM", "fsi:strategy:momentum", 0.85, 42, "ACTIVE");

        listener.onTrustChanged(event);

        assertEquals(1, broadcasts.size());
        assertEquals("trust:MOMENTUM", broadcasts.get(0).topic);
        var payload = (TradingPushPayload.TrustUpdate) broadcasts.get(0).event;
        assertEquals("TRUST_UPDATE", payload.type());
        assertEquals("MOMENTUM", payload.strategyType());
        assertEquals("fsi:strategy:momentum", payload.actorId());
        assertEquals(0.85, payload.trustScore());
        assertEquals(42, payload.decisionCount());
        assertEquals("ACTIVE", payload.phase());
    }

    @Test
    void routingDecision_broadcastsToRoutingTopic() {
        var evalId = UUID.randomUUID();
        var now = Instant.now();
        var event = new RoutingDecisionEvent(
                evalId, "AAPL",
                List.of("fsi:strategy:momentum", "fsi:strategy:mean-reversion"),
                "LLM_ROUTED", now);

        listener.onRoutingDecision(event);

        assertEquals(1, broadcasts.size());
        assertEquals("routing:latest", broadcasts.get(0).topic);
        var payload = (TradingPushPayload.RoutingUpdate) broadcasts.get(0).event;
        assertEquals("ROUTING_UPDATE", payload.type());
        assertEquals(evalId, payload.evaluationId());
        assertEquals("AAPL", payload.instrument());
        assertEquals(List.of("fsi:strategy:momentum", "fsi:strategy:mean-reversion"),
                payload.selectedAgents());
        assertEquals("LLM_ROUTED", payload.routingStrategy());
        assertEquals(now, payload.decidedAt());
    }

    record BroadcastCapture(String topic, Object event) {}
}
