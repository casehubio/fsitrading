package io.casehub.fsitrading.app.pipeline;

import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.RegimeChanged;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendReversalDetected;
import io.casehub.fsitrading.model.TrendSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FsiArenaEventBridgeTest {

    private final List<MarketSignal> triggered = new ArrayList<>();
    private FsiArenaEventBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new FsiArenaEventBridge(triggered::add);
    }

    @Test
    void trendReversal_triggersArenaWithSignal() {
        var now = Instant.now();
        var trend = new TrendSummary("AAPL", TrendDirection.DOWN, -0.02, 0.01, "FLAT", now, now);
        var event = new TrendReversalDetected("AAPL", TrendDirection.UP, TrendDirection.DOWN, trend);

        bridge.onTrendReversal(event);

        assertEquals(1, triggered.size());
        assertEquals("AAPL", triggered.get(0).instrument());
        assertEquals("TREND_REVERSAL", triggered.get(0).eventType());
    }

    @Test
    void regimeChange_triggersArenaWithSignal() {
        var now = Instant.now();
        var assessment = new RegimeAssessment("MSFT", MarketRegime.VOLATILE, 0.90, "volatile", now);
        var event = new RegimeChanged("MSFT", MarketRegime.TRENDING, MarketRegime.VOLATILE, assessment);

        bridge.onRegimeChanged(event);

        assertEquals(1, triggered.size());
        assertEquals("MSFT", triggered.get(0).instrument());
        assertEquals("REGIME_CHANGE", triggered.get(0).eventType());
    }
}
