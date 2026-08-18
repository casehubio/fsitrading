package io.casehub.fsitrading.app.push;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradingPushPayloadTest {

    @Test
    void positionUpdate_typeDiscriminator() {
        var p = new TradingPushPayload.PositionUpdate(
                UUID.randomUUID(), "AAPL", "EQUITY", UUID.randomUUID(),
                BigDecimal.TEN, BigDecimal.ONE, null, Instant.now());
        assertEquals("POSITION_UPDATE", p.type());
        assertInstanceOf(TradingPushPayload.class, p);
    }

    @Test
    void positionUpdate_carriesAllFields() {
        var posId = UUID.randomUUID();
        var stratId = UUID.randomUUID();
        var now = Instant.now();
        var p = new TradingPushPayload.PositionUpdate(
                posId, "MSFT", "EQUITY", stratId,
                BigDecimal.valueOf(100), BigDecimal.valueOf(350),
                BigDecimal.valueOf(500), now);
        assertEquals(posId, p.positionId());
        assertEquals("MSFT", p.instrument());
        assertEquals("EQUITY", p.assetClass());
        assertEquals(stratId, p.strategyId());
        assertEquals(BigDecimal.valueOf(100), p.quantity());
        assertEquals(BigDecimal.valueOf(350), p.avgCost());
        assertEquals(BigDecimal.valueOf(500), p.realizedPnl());
        assertEquals(now, p.updatedAt());
    }

    @Test
    void pnlUpdate_typeDiscriminator() {
        var p = new TradingPushPayload.PnlUpdate(
                UUID.randomUUID(), "AAPL", BigDecimal.valueOf(500),
                BigDecimal.valueOf(150), BigDecimal.valueOf(10), Instant.now());
        assertEquals("PNL_UPDATE", p.type());
    }

    @Test
    void trustUpdate_typeDiscriminator() {
        var p = new TradingPushPayload.TrustUpdate(
                "MOMENTUM", "fsi:strategy:momentum", 0.85, 42, "ACTIVE");
        assertEquals("TRUST_UPDATE", p.type());
        assertEquals("MOMENTUM", p.strategyType());
        assertEquals(0.85, p.trustScore());
        assertEquals(42, p.decisionCount());
        assertEquals("ACTIVE", p.phase());
    }

    @Test
    void routingUpdate_typeDiscriminator() {
        var agents = List.of("fsi:strategy:momentum", "fsi:strategy:mean-reversion");
        var p = new TradingPushPayload.RoutingUpdate(
                UUID.randomUUID(), "AAPL", agents, "LLM_ROUTED", Instant.now());
        assertEquals("ROUTING_UPDATE", p.type());
        assertEquals(2, p.selectedAgents().size());
        assertEquals("AAPL", p.instrument());
        assertEquals("LLM_ROUTED", p.routingStrategy());
    }
}
