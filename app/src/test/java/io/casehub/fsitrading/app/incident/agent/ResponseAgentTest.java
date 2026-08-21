package io.casehub.fsitrading.app.incident.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResponseAgentTest {

    private static final Map<String, Object> INCIDENT_INPUT = Map.of(
            "severity", "CRITICAL",
            "eventType", "FLASH_CRASH",
            "instruments", List.of("AAPL", "MSFT"));

    // --- Rule-based agents ---

    @Test
    void emergencyHalt_haltsTrading() {
        var result = new EmergencyHaltAgent().execute(INCIDENT_INPUT);
        assertEquals("emergency-halt", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("rule-based", result.get("agentType"));
        assertNotNull(result.get("description"));
    }

    @Test
    void closePositions_closesAtMarket() {
        var result = new ClosePositionsAgent().execute(INCIDENT_INPUT);
        assertEquals("close-positions", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("rule-based", result.get("agentType"));
    }

    @Test
    void haltAndWait_suspendsTrading() {
        var input = Map.<String, Object>of("severity", "HIGH",
                "eventType", "CIRCUIT_BREAKER", "instruments", List.of("TSLA"));
        var result = new HaltAndWaitAgent().execute(input);
        assertEquals("halt-and-wait", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("rule-based", result.get("agentType"));
    }

    @Test
    void alertOncall_createsNotification() {
        var result = new AlertOncallAgent().execute(INCIDENT_INPUT);
        assertEquals("alert-oncall", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("rule-based", result.get("agentType"));
        assertNotNull(result.get("alertTarget"));
    }

    @Test
    void adjustLimits_tightensLimits() {
        var input = Map.<String, Object>of("severity", "MEDIUM",
                "eventType", "NEWS_EVENT", "instruments", List.of("GOOG"));
        var result = new AdjustLimitsAgent().execute(input);
        assertEquals("adjust-limits", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("rule-based", result.get("agentType"));
    }

    @Test
    void monitor_setsThresholds() {
        var input = Map.<String, Object>of("severity", "MEDIUM",
                "eventType", "GAP_OPEN", "instruments", List.of("AMZN"));
        var result = new MonitorAgent().execute(input);
        assertEquals("monitor", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("rule-based", result.get("agentType"));
    }

    @Test
    void verify_validatesPositionState() {
        var result = new VerifyAgent().execute(INCIDENT_INPUT);
        assertEquals("verify", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("rule-based", result.get("agentType"));
    }

    // --- LLM agents (stubs) ---

    @Test
    void positionReducer_decidesReduction() {
        var input = Map.<String, Object>of("severity", "HIGH",
                "eventType", "LIQUIDITY_DROP", "instruments", List.of("MSFT"));
        var result = new PositionReducerAgent().execute(input);
        assertEquals("reduce-exposure", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("llm", result.get("agentType"));
    }

    @Test
    void hedge_selectsHedgingInstruments() {
        var input = Map.<String, Object>of("severity", "HIGH",
                "eventType", "LIQUIDITY_DROP", "instruments", List.of("AAPL"));
        var result = new HedgeAgent().execute(input);
        assertEquals("hedge", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("llm", result.get("agentType"));
    }

    @Test
    void reEvaluator_assessesGapImpact() {
        var input = Map.<String, Object>of("severity", "HIGH",
                "eventType", "GAP_OPEN", "instruments", List.of("TSLA"));
        var result = new ReEvaluatorAgent().execute(input);
        assertEquals("re-evaluate", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("llm", result.get("agentType"));
    }

    @Test
    void exposureCloser_prioritisesExposure() {
        var input = Map.<String, Object>of("severity", "CRITICAL",
                "eventType", "COUNTERPARTY_FAILURE", "instruments", List.of("AAPL"));
        var result = new ExposureCloserAgent().execute(input);
        assertEquals("close-exposure", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("llm", result.get("agentType"));
    }

    @Test
    void sentimentAnalyser_interpretsNews() {
        var input = Map.<String, Object>of("severity", "MEDIUM",
                "eventType", "NEWS_EVENT", "instruments", List.of("GOOG"));
        var result = new SentimentAnalyserAgent().execute(input);
        assertEquals("analyse-sentiment", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("llm", result.get("agentType"));
    }

    @Test
    void liquidation_plansOrderlyLiquidation() {
        var input = Map.<String, Object>of("severity", "CRITICAL",
                "eventType", "MARGIN_CALL", "instruments", List.of("AAPL", "MSFT"));
        var result = new LiquidationAgent().execute(input);
        assertEquals("liquidate", result.get("action"));
        assertEquals("completed", result.get("status"));
        assertEquals("llm", result.get("agentType"));
    }

    // --- Structural ---

    @Test
    void allAgents_includeInstrumentsInResult() {
        List<IncidentResponseAgent> agents = List.of(
                new EmergencyHaltAgent(), new ClosePositionsAgent(),
                new HaltAndWaitAgent(), new AlertOncallAgent(),
                new AdjustLimitsAgent(), new MonitorAgent(),
                new VerifyAgent(), new PositionReducerAgent(),
                new HedgeAgent(), new ReEvaluatorAgent(),
                new ExposureCloserAgent(), new SentimentAnalyserAgent(),
                new LiquidationAgent());
        assertEquals(13, agents.size());
        for (IncidentResponseAgent agent : agents) {
            var result = agent.execute(INCIDENT_INPUT);
            assertNotNull(result.get("instruments"), agent.getClass().getSimpleName() + " missing instruments");
        }
    }
}
