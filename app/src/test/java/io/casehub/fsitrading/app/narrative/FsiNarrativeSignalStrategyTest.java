package io.casehub.fsitrading.app.narrative;

import io.casehub.api.spi.StepOutcomeEvent;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import io.casehub.blocks.summarisation.narrative.RoutingDecision;
import io.casehub.blocks.summarisation.narrative.StepOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FsiNarrativeSignalStrategyTest {

    private EventStreamBus<DecisionSignal> signalBus;
    private List<DecisionSignal> captured;
    private FsiNarrativeSignalStrategy strategy;

    @BeforeEach
    void setUp() {
        signalBus = new EventStreamBus<>();
        captured = new ArrayList<>();
        signalBus.subscribe(e -> true, (LevelEvent<DecisionSignal> event) -> captured.add(event.payload()));
        var pipeline = mock(DecisionNarrativePipeline.class);
        strategy = new FsiNarrativeSignalStrategy(signalBus, pipeline);
    }

    @Test
    void emitsStepOutcomeSignal() {
        strategy.onStepOutcome(stepEvent(RoutingOutcome.SUCCESS));

        assertThat(captured).anyMatch(s -> s instanceof StepOutcome);
    }

    @Test
    void emitsRoutingDecisionWhenContextHasRoutingData() {
        var event = new StepOutcomeEvent(UUID.randomUUID(), "tenant-1",
                "overnight-incident", "reduce-exposure", "risk-management",
                "ConservativeHedge", RoutingOutcome.SUCCESS,
                Map.of("instrument", "AAPL", "detectedAt", "2026-09-01T14:30:00Z",
                        "routedAgentId", "agent-1", "routingScore", "0.82"),
                Duration.ofSeconds(5));

        strategy.onStepOutcome(event);

        assertThat(captured).anyMatch(s -> s instanceof RoutingDecision);
    }

    @Test
    void skipsNonOvernightIncidentCaseType() {
        var event = new StepOutcomeEvent(UUID.randomUUID(), "tenant-1",
                "other-case", "step-1", "cap-1", "worker-1",
                RoutingOutcome.SUCCESS,
                Map.of("instrument", "AAPL"),
                Duration.ofSeconds(5));

        strategy.onStepOutcome(event);

        assertThat(captured).isEmpty();
    }

    private StepOutcomeEvent stepEvent(RoutingOutcome outcome) {
        return new StepOutcomeEvent(UUID.randomUUID(), "tenant-1",
                "overnight-incident", "reduce-exposure", "risk-management",
                "ConservativeHedge", outcome,
                Map.of("instrument", "AAPL", "detectedAt", "2026-09-01T14:30:00Z"),
                Duration.ofSeconds(5));
    }
}
