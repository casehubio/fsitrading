package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.MarketEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OvernightIncidentCaseDescriptorTest {

    private final OvernightIncidentCaseDescriptor descriptor = new OvernightIncidentCaseDescriptor();

    @Test
    void decompositionSteps_critical_matchesSeverityDescriptor() {
        var steps = descriptor.decompositionStepsFor(IncidentSeverity.CRITICAL);
        assertEquals(4, steps.size());
        assertEquals("emergency-halt", steps.get(0));
        assertEquals("verify", steps.get(3));
    }

    @Test
    void decompositionSteps_high_matchesSeverityDescriptor() {
        var steps = descriptor.decompositionStepsFor(IncidentSeverity.HIGH);
        assertEquals(4, steps.size());
        assertEquals("reduce-exposure", steps.get(0));
    }

    @Test
    void decompositionSteps_medium_matchesSeverityDescriptor() {
        var steps = descriptor.decompositionStepsFor(IncidentSeverity.MEDIUM);
        assertEquals(3, steps.size());
        assertEquals("adjust-limits", steps.get(0));
    }

    @Test
    void agentFor_flashCrash_emergencyHaltAgent() {
        assertEquals("emergencyHaltAgent",
                descriptor.agentNameFor(MarketEventType.FLASH_CRASH));
    }

    @Test
    void agentFor_marginCall_liquidationAgent() {
        assertEquals("liquidationAgent",
                descriptor.agentNameFor(MarketEventType.MARGIN_CALL));
    }

    @Test
    void agentFor_counterpartyFailure_exposureCloserAgent() {
        assertEquals("exposureCloserAgent",
                descriptor.agentNameFor(MarketEventType.COUNTERPARTY_FAILURE));
    }

    @Test
    void agentFor_allRoutableTypes_returnsNonNull() {
        for (var type : new MarketEventType[]{
                MarketEventType.FLASH_CRASH, MarketEventType.LIQUIDITY_DROP,
                MarketEventType.GAP_OPEN, MarketEventType.COUNTERPARTY_FAILURE,
                MarketEventType.CIRCUIT_BREAKER, MarketEventType.NEWS_EVENT,
                MarketEventType.MARGIN_CALL}) {
            assertEquals(
                    io.casehub.fsitrading.model.MarketEventTypeDescriptor.forType(type).agentName(),
                    descriptor.agentNameFor(type));
        }
    }
}
