package io.casehub.fsitrading.app.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.TrendSummary;
import org.jboss.logging.Logger;


import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class FsiRegimeSummariser implements Summariser<TrendSummary, RegimeAssessment> {

    private static final Logger log = Logger.getLogger(FsiRegimeSummariser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Function<String, CompletionStage<String>> llmProvider;

    public FsiRegimeSummariser(Function<String, CompletionStage<String>> llmProvider) {
        this.llmProvider = llmProvider;
    }

    @Override
    public CompletionStage<List<RegimeAssessment>> summarise(List<LevelEvent<TrendSummary>> batch) {
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        var trends = batch.stream().map(LevelEvent::payload).toList();
        String instrument = trends.get(0).instrument();
        String prompt = buildPrompt(trends);

        return llmProvider.apply(prompt)
                .thenApply(response -> parseResponse(response, instrument))
                .exceptionally(ex -> {
                    log.warnf(ex, "LLM regime assessment failed for %s", instrument);
                    return List.of();
                });
    }

    private String buildPrompt(List<TrendSummary> trends) {
        var sb = new StringBuilder();
        sb.append("Analyze the following market trend data and classify the current market regime.\n\n");
        for (var trend : trends) {
            sb.append(String.format("Instrument: %s, Direction: %s, Momentum: %.4f, Volatility: %.4f, Volume: %s, Window: %s to %s%n",
                    trend.instrument(), trend.direction(), trend.momentum(),
                    trend.volatility(), trend.volumeProfile(),
                    trend.windowStart(), trend.windowEnd()));
        }
        sb.append("\nRespond with a JSON object: {\"regime\": \"TRENDING|MEAN_REVERTING|VOLATILE|QUIET\", \"confidence\": 0.0-1.0, \"rationale\": \"...\"}");
        return sb.toString();
    }

    private List<RegimeAssessment> parseResponse(String response, String instrument) {
        try {
            var node = MAPPER.readTree(response);
            MarketRegime regime = MarketRegime.valueOf(node.get("regime").asText());
            double confidence = node.get("confidence").asDouble();
            String rationale = node.get("rationale").asText();
            return List.of(new RegimeAssessment(instrument, regime, confidence, rationale, Instant.now()));
        } catch (Exception e) {
            log.warnf(e, "Failed to parse LLM response for regime assessment: %s", response);
            return List.of();
        }
    }
}
