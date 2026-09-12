package io.casehub.fsitrading.app.narrative;

import io.casehub.api.spi.StepOutcomeEvent;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.narrative.AbstractNarrativeSignalStrategy;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import io.casehub.blocks.summarisation.narrative.RoutingDecision;
import io.casehub.blocks.summarisation.narrative.StepOutcome;
import java.time.Duration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Alternative
@Priority(1)
public class FsiNarrativeSignalStrategy extends AbstractNarrativeSignalStrategy {

    private static final String CASE_TYPE = "overnight-incident";

    @Inject
    public FsiNarrativeSignalStrategy(EventStreamBus<DecisionSignal> signalBus,
                                       DecisionNarrativePipeline pipeline) {
        super(signalBus, pipeline);
    }

    @Override
    public void onStepOutcome(Object event) {
        if (!(event instanceof StepOutcomeEvent step)) return;
        if (!CASE_TYPE.equals(step.caseType())) return;

        String caseId = step.caseId().toString();
        String stepName = step.bindingName();
        Instant now = Instant.now();

        emit(new StepOutcome(caseId, stepName, now,
                step.outcome().name(), step.workerName(),
                null, step.executionDuration() != null ? step.executionDuration() : Duration.ZERO));

        Map<String, Object> snapshot = step.contextSnapshot();
        String routedAgentId = (String) snapshot.get("routedAgentId");
        if (routedAgentId != null) {
            String scoreStr = (String) snapshot.get("routingScore");
            double score = 0.0;
            if (scoreStr != null) {
                try { score = Double.parseDouble(scoreStr); } catch (NumberFormatException ignored) {}
            }
            emit(new RoutingDecision(caseId, stepName, now,
                    routedAgentId, "trust-weighted", score,
                    List.of(step.workerName()), null));
        }
    }

    @Override
    protected @Nullable String extractTenancyId(DecisionSignal signal) {
        return null;
    }
}
