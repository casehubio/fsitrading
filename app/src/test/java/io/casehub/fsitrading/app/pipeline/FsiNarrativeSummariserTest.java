package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.SessionNarrative;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class FsiNarrativeSummariserTest {

    @Test
    void convertsRegimeAssessmentsToNarrative() throws Exception {
        var summariser = new FsiNarrativeSummariser(
                text -> CompletableFuture.completedFuture("Market session summary: AAPL trending, MSFT volatile"));

        var events = List.of(
                regimeEvent("AAPL", MarketRegime.TRENDING),
                regimeEvent("MSFT", MarketRegime.VOLATILE));

        CompletionStage<List<SessionNarrative>> result = summariser.summarise(events);
        List<SessionNarrative> narratives = result.toCompletableFuture().join();

        assertEquals(1, narratives.size());
        SessionNarrative narrative = narratives.get(0);
        assertEquals(2, narrative.instruments().size());
        assertTrue(narrative.instruments().contains("AAPL"));
        assertTrue(narrative.instruments().contains("MSFT"));
        assertFalse(narrative.narrative().isEmpty());
    }

    @Test
    void gracefullyHandlesLlmFailure() throws Exception {
        var summariser = new FsiNarrativeSummariser(
                text -> CompletableFuture.failedFuture(new RuntimeException("LLM unavailable")));

        var events = List.of(regimeEvent("AAPL", MarketRegime.TRENDING));

        CompletionStage<List<SessionNarrative>> result = summariser.summarise(events);
        List<SessionNarrative> narratives = result.toCompletableFuture().join();

        assertTrue(narratives.isEmpty(), "Should return empty list on LLM failure");
    }

    @Test
    void emptyBatchReturnsEmpty() throws Exception {
        var summariser = new FsiNarrativeSummariser(
                text -> CompletableFuture.completedFuture("summary"));

        CompletionStage<List<SessionNarrative>> result = summariser.summarise(List.of());
        List<SessionNarrative> narratives = result.toCompletableFuture().join();

        assertTrue(narratives.isEmpty());
    }

    private LevelEvent<RegimeAssessment> regimeEvent(String instrument, MarketRegime regime) {
        var now = Instant.now();
        var assessment = new RegimeAssessment(instrument, regime, 0.8, "test rationale", now);
        return new LevelEvent<>(assessment, now.toEpochMilli(), FsiEventLevels.REGIME_1H);
    }
}
