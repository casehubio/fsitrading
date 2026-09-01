package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.app.pipeline.MarketPulseState;
import io.casehub.neocortex.memory.cbr.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FsiPlanAdapterTest {

    private AgentTrustProvider trustProvider;
    private MarketPulseState pulseState;
    private FsiPlanAdapter adapter;

    @BeforeEach
    void setUp() {
        trustProvider = mock(AgentTrustProvider.class);
        pulseState = mock(MarketPulseState.class);
        adapter = new FsiPlanAdapter(trustProvider, pulseState);
        when(trustProvider.currentTrustScore(anyString())).thenReturn(OptionalDouble.empty());
    }

    @Test
    void retainsStepWhenNoAdaptationNeeded() {
        when(pulseState.isMarketClosed()).thenReturn(false);
        var plan = adapter.adapt("plan", scoredCase(0.8, 10.0), currentFeatures(10.0));
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().action()).isEqualTo(AdaptationAction.RETAINED);
    }

    @Test
    void substitutesLowTrustAgent() {
        when(trustProvider.currentTrustScore("lowTrustAgent")).thenReturn(OptionalDouble.of(0.2));
        when(pulseState.isMarketClosed()).thenReturn(false);
        var plan = adapter.adapt("plan", scoredCaseWithAgent("lowTrustAgent", 0.8, 10.0),
                currentFeatures(10.0));
        assertThat(plan.steps().getFirst().action()).isEqualTo(AdaptationAction.SUBSTITUTED);
        assertThat(plan.steps().getFirst().reason()).contains("trust");
    }

    @Test
    void retainsWhenTrustScoreUnknown() {
        when(trustProvider.currentTrustScore("unknownAgent")).thenReturn(OptionalDouble.empty());
        when(pulseState.isMarketClosed()).thenReturn(false);
        var plan = adapter.adapt("plan", scoredCaseWithAgent("unknownAgent", 0.8, 10.0),
                currentFeatures(10.0));
        assertThat(plan.steps().getFirst().action()).isEqualTo(AdaptationAction.RETAINED);
    }

    @Test
    void boostsStepWhenVolatilityDoubled() {
        when(pulseState.isMarketClosed()).thenReturn(false);
        var plan = adapter.adapt("plan", scoredCase(0.8, 10.0), currentFeatures(25.0));
        assertThat(plan.steps().getFirst().action()).isEqualTo(AdaptationAction.BOOSTED);
    }

    @Test
    void suppressesStepWhenMarketClosed() {
        when(pulseState.isMarketClosed()).thenReturn(true);
        var plan = adapter.adapt("plan", scoredCase(0.8, 10.0), currentFeatures(10.0));
        assertThat(plan.steps().getFirst().action()).isEqualTo(AdaptationAction.SUPPRESSED);
    }

    @Test
    void addsPreReduceStepForHighSeverityEvent() {
        when(pulseState.isMarketClosed()).thenReturn(false);
        var plan = adapter.adapt("plan", scoredCase(0.8, 10.0), currentFeatures(10.0, "FLASH_CRASH"));
        assertThat(plan.steps()).hasSizeGreaterThan(1);
        assertThat(plan.steps().getFirst().action()).isEqualTo(AdaptationAction.ADDED);
        assertThat(plan.steps().getFirst().bindingName()).isEqualTo("pre-reduce");
        assertThat(plan.steps().getFirst().reason()).contains("FLASH_CRASH");
    }

    @Test
    void noPreReduceForLowSeverityEvent() {
        when(pulseState.isMarketClosed()).thenReturn(false);
        var features = Map.of(
                "volatility_at_detection", (FeatureValue) FeatureValue.number(10.0),
                "event_type", (FeatureValue) FeatureValue.string("NEWS_EVENT"));
        var plan = adapter.adapt("plan", scoredCase(0.8, 10.0), features);
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().action()).isEqualTo(AdaptationAction.RETAINED);
    }

    @Test
    void suppressionTakesPrecedenceOverOtherStrategies() {
        when(pulseState.isMarketClosed()).thenReturn(true);
        when(trustProvider.currentTrustScore("agent1")).thenReturn(OptionalDouble.of(0.1));
        var plan = adapter.adapt("plan", scoredCase(0.8, 5.0), currentFeatures(25.0));
        assertThat(plan.steps().getFirst().action()).isEqualTo(AdaptationAction.SUPPRESSED);
    }

    private ScoredCbrCase<PlanCbrCase> scoredCase(double score, double volatility) {
        return scoredCaseWithAgent("agent1", score, volatility);
    }

    private ScoredCbrCase<PlanCbrCase> scoredCaseWithAgent(String agent, double score,
                                                            double volatility) {
        var trace = new PlanTrace("respond", "incident-respond", agent,
                "SUCCESS", 1, Map.of(), null);
        var features = Map.of(
                "volatility_at_detection", (FeatureValue) FeatureValue.number(volatility),
                "event_type", (FeatureValue) FeatureValue.string("FLASH_CRASH"));
        var planCase = new PlanCbrCase("incident", "response", null, null,
                features, List.of(trace), 0.8, agent);
        return new ScoredCbrCase<>(planCase, "case-1", score);
    }

    private Map<String, FeatureValue> currentFeatures(double volatility) {
        return currentFeatures(volatility, "NEWS_EVENT");
    }

    private Map<String, FeatureValue> currentFeatures(double volatility, String eventType) {
        return Map.of(
                "volatility_at_detection", FeatureValue.number(volatility),
                "event_type", FeatureValue.string(eventType));
    }
}
