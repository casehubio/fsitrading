package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class MarketEventTypeDescriptorTest {

    @Test
    void flashCrash_routesToEmergencyHalt() {
        var desc = MarketEventTypeDescriptor.forType(MarketEventType.FLASH_CRASH);
        assertEquals("emergencyHaltAgent", desc.agentName());
        assertEquals("Market-detected", desc.eventSource());
        assertEquals("halt", desc.fallbackAction());
    }

    @Test
    void liquidityDrop_routesToPositionReducer() {
        var desc = MarketEventTypeDescriptor.forType(MarketEventType.LIQUIDITY_DROP);
        assertEquals("positionReducerAgent", desc.agentName());
        assertEquals("reduce", desc.fallbackAction());
    }

    @Test
    void gapOpen_routesToReEvaluator() {
        var desc = MarketEventTypeDescriptor.forType(MarketEventType.GAP_OPEN);
        assertEquals("reEvaluatorAgent", desc.agentName());
        assertEquals("monitor", desc.fallbackAction());
    }

    @Test
    void counterpartyFailure_isExternalEvent() {
        var desc = MarketEventTypeDescriptor.forType(MarketEventType.COUNTERPARTY_FAILURE);
        assertEquals("exposureCloserAgent", desc.agentName());
        assertEquals("External", desc.eventSource());
        assertEquals("halt", desc.fallbackAction());
    }

    @Test
    void circuitBreaker_routesToHaltAndWait() {
        var desc = MarketEventTypeDescriptor.forType(MarketEventType.CIRCUIT_BREAKER);
        assertEquals("haltAndWaitAgent", desc.agentName());
    }

    @Test
    void newsEvent_routesToSentimentAnalyser() {
        var desc = MarketEventTypeDescriptor.forType(MarketEventType.NEWS_EVENT);
        assertEquals("sentimentAnalyserAgent", desc.agentName());
        assertEquals("monitor", desc.fallbackAction());
    }

    @Test
    void marginCall_isExternalEvent() {
        var desc = MarketEventTypeDescriptor.forType(MarketEventType.MARGIN_CALL);
        assertEquals("liquidationAgent", desc.agentName());
        assertEquals("External", desc.eventSource());
        assertEquals("reduce", desc.fallbackAction());
    }

    @Test
    void nonRoutableEvents_returnNull() {
        assertNull(MarketEventTypeDescriptor.forType(MarketEventType.PRICE_TICK));
        assertNull(MarketEventTypeDescriptor.forType(MarketEventType.VOLUME_SPIKE));
    }

    @ParameterizedTest
    @EnumSource(value = MarketEventType.class, names = {"PRICE_TICK", "VOLUME_SPIKE"}, mode = EnumSource.Mode.EXCLUDE)
    void allRoutableTypes_haveDescriptors(MarketEventType type) {
        var desc = MarketEventTypeDescriptor.forType(type);
        assertNotNull(desc, "Missing descriptor for " + type);
        assertNotNull(desc.agentName());
        assertNotNull(desc.eventSource());
        assertNotNull(desc.fallbackAction());
    }
}
