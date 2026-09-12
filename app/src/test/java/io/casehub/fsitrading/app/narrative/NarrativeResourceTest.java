package io.casehub.fsitrading.app.narrative;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.narrative.DecisionNarrative;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrativeResourceTest {

    private EventStreamBus<DecisionNarrative> narrativeBus;
    private NarrativeResource resource;

    @BeforeEach
    void setUp() {
        narrativeBus = new EventStreamBus<>();
        var pipeline = mock(DecisionNarrativePipeline.class);
        when(pipeline.narrativeBus()).thenReturn(narrativeBus);
        resource = new NarrativeResource(pipeline);
    }

    @Test
    void returnsEmptyWhenNoCaseData() {
        var result = resource.get("case-unknown");
        assertThat(result).isEmpty();
    }

    @Test
    void cachesNarrative() {
        var narrative = new DecisionNarrative("case-1", List.of("step-1"),
                "Chose ConservativeHedge over MomentumBurst",
                List.of("routing-record-1"), 0.85, Instant.now());
        narrativeBus.publish(new LevelEvent<>(narrative, Instant.now().toEpochMilli(),
                new io.casehub.blocks.summarisation.EventLevel("decision-narratives", 2), null));

        var result = resource.get("case-1");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().explanation()).contains("ConservativeHedge");
    }

    @Test
    void accumulatesMultipleNarrativesPerCase() {
        var n1 = new DecisionNarrative("case-1", List.of("step-1"),
                "First step", List.of(), 0.8, Instant.now());
        var n2 = new DecisionNarrative("case-1", List.of("step-2"),
                "Second step", List.of(), 0.9, Instant.now());
        narrativeBus.publish(new LevelEvent<>(n1, Instant.now().toEpochMilli(),
                new io.casehub.blocks.summarisation.EventLevel("decision-narratives", 2), null));
        narrativeBus.publish(new LevelEvent<>(n2, Instant.now().toEpochMilli(),
                new io.casehub.blocks.summarisation.EventLevel("decision-narratives", 2), null));

        var result = resource.get("case-1");
        assertThat(result).hasSize(2);
    }
}
