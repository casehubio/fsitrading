package io.casehub.fsitrading.app.pipeline;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MarketPulseOrchestrationTest {

    @Test
    void stateStartsOpen() {
        var state = new MarketPulseState();
        assertFalse(state.isMarketClosed());
        assertFalse(state.isShutdownRequested());
    }

    @Test
    void marketCloseExitsLoop() {
        var state = new MarketPulseState();
        state.setMarketClosed(true);
        assertTrue(state.isMarketClosed());
        assertTrue(state.shouldExit());
    }

    @Test
    void shutdownExitsLoop() {
        var state = new MarketPulseState();
        state.setShutdownRequested(true);
        assertTrue(state.isShutdownRequested());
        assertTrue(state.shouldExit());
    }

    @Test
    void sessionBoundaries() {
        var state = new MarketPulseState();
        var start = Instant.now();
        var end = start.plusSeconds(23400);
        state.setSessionStart(start);
        state.setSessionEnd(end);

        assertEquals(start, state.getSessionStart());
        assertEquals(end, state.getSessionEnd());
    }

    @Test
    void lastNarrativeTimestampTracked() {
        var state = new MarketPulseState();
        assertNull(state.getLastNarrativeTimestamp());
        var now = Instant.now();
        state.setLastNarrativeTimestamp(now);
        assertEquals(now, state.getLastNarrativeTimestamp());
    }

    @Test
    void shouldExitIsFalseInitially() {
        var state = new MarketPulseState();
        assertFalse(state.shouldExit());
    }
}
