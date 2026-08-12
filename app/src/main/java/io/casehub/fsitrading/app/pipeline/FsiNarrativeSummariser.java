package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.SessionNarrative;
import org.jboss.logging.Logger;


import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FsiNarrativeSummariser implements Summariser<RegimeAssessment, SessionNarrative> {

    private static final Logger log = Logger.getLogger(FsiNarrativeSummariser.class);

    private final Function<String, CompletionStage<String>> contentSummariser;

    public FsiNarrativeSummariser(Function<String, CompletionStage<String>> contentSummariser) {
        this.contentSummariser = contentSummariser;
    }

    @Override
    public CompletionStage<List<SessionNarrative>> summarise(List<LevelEvent<RegimeAssessment>> batch) {
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        var assessments = batch.stream().map(LevelEvent::payload).toList();
        var instruments = assessments.stream()
                .map(RegimeAssessment::instrument)
                .distinct()
                .toList();

        String input = renderAssessmentsAsText(assessments);

        return contentSummariser.apply(input)
                .thenApply(narrative -> List.of(
                        new SessionNarrative(instruments, narrative, Instant.now())))
                .exceptionally(ex -> {
                    log.warnf(ex, "LLM narrative summarisation failed for %d instruments",
                            instruments.size());
                    return List.of();
                });
    }

    private String renderAssessmentsAsText(List<RegimeAssessment> assessments) {
        return assessments.stream()
                .map(a -> String.format("%s: %s (confidence: %.2f) - %s",
                        a.instrument(), a.regime(), a.confidence(), a.rationale()))
                .collect(Collectors.joining("\n"));
    }
}
