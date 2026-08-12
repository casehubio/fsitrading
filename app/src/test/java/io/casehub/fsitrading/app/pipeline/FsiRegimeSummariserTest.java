package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class FsiRegimeSummariserTest {

    @Test
    void parsesRegimeAssessmentFromJson() throws Exception {
        String llmResponse = """
                {"regime": "TRENDING", "confidence": 0.85, "rationale": "Sustained upward momentum"}""";

        var summariser = new FsiRegimeSummariser(prompt -> CompletableFuture.completedFuture(llmResponse));

        var trendEvents = createTrendEvents("AAPL", TrendDirection.UP);
        CompletionStage<List<RegimeAssessment>> result = summariser.summarise(trendEvents);
        List<RegimeAssessment> assessments = result.toCompletableFuture().join();

        assertEquals(1, assessments.size());
        assertEquals("AAPL", assessments.get(0).instrument());
        assertEquals(MarketRegime.TRENDING, assessments.get(0).regime());
        assertEquals(0.85, assessments.get(0).confidence(), 0.001);
        assertEquals("Sustained upward momentum", assessments.get(0).rationale());
    }

    @Test
    void gracefullyHandlesLlmFailure() throws Exception {
        var summariser = new FsiRegimeSummariser(prompt ->
                CompletableFuture.failedFuture(new RuntimeException("LLM provider unavailable")));

        var trendEvents = createTrendEvents("AAPL", TrendDirection.UP);
        CompletionStage<List<RegimeAssessment>> result = summariser.summarise(trendEvents);
        List<RegimeAssessment> assessments = result.toCompletableFuture().join();

        assertTrue(assessments.isEmpty(), "Should return empty list on LLM failure");
    }

    @Test
    void gracefullyHandlesMalformedJson() throws Exception {
        var summariser = new FsiRegimeSummariser(prompt ->
                CompletableFuture.completedFuture("not valid json at all"));

        var trendEvents = createTrendEvents("AAPL", TrendDirection.UP);
        CompletionStage<List<RegimeAssessment>> result = summariser.summarise(trendEvents);
        List<RegimeAssessment> assessments = result.toCompletableFuture().join();

        assertTrue(assessments.isEmpty(), "Should return empty list on malformed JSON");
    }

    @Test
    void emptyBatchReturnsEmpty() throws Exception {
        var summariser = new FsiRegimeSummariser(prompt -> CompletableFuture.completedFuture("{}"));

        CompletionStage<List<RegimeAssessment>> result = summariser.summarise(List.of());
        List<RegimeAssessment> assessments = result.toCompletableFuture().join();

        assertTrue(assessments.isEmpty());
    }

    private List<LevelEvent<TrendSummary>> createTrendEvents(String instrument, TrendDirection direction) {
        var now = Instant.now();
        var trend = new TrendSummary(instrument, direction, 0.02, 0.01, "INCREASING",
                now.minusSeconds(300), now);
        return List.of(new LevelEvent<>(trend, now.toEpochMilli(), FsiEventLevels.TREND_5M));
    }
}
