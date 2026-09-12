package io.casehub.fsitrading.app.cbr;

import io.casehub.api.spi.StepOutcomeEvent;
import io.casehub.api.spi.StepOutcomeObserver;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiStepOutcomeObserver implements StepOutcomeObserver {

    private static final String CASE_TYPE = "overnight-incident";

    private final CbrCaseMemoryStore cbrStore;
    private final FsiFeatureExtractor featureExtractor;

    @Inject
    public FsiStepOutcomeObserver(CbrCaseMemoryStore cbrStore,
                                  FsiFeatureExtractor featureExtractor) {
        this.cbrStore = cbrStore;
        this.featureExtractor = featureExtractor;
    }

    @Override
    public void onStepOutcome(StepOutcomeEvent event) {
        if (!CASE_TYPE.equals(event.caseType())) return;

        Map<String, Object> snapshot = event.contextSnapshot();
        String detectedAt = (String) snapshot.get("detectedAt");
        if (detectedAt == null) return;
        Instant detection = Instant.parse(detectedAt);

        Map<String, Object> rawFeatures = featureExtractor.extractFromSnapshot(snapshot, detection);
        Map<String, FeatureValue> features = FeatureValue.toFeatureMap(rawFeatures);

        String problem = event.bindingName() + " executed by " + event.workerName();
        double confidence = event.outcome().name().equals("SUCCESS") ? 1.0 : 0.0;

        FeatureVectorCbrCase cbrCase = new FeatureVectorCbrCase(
                problem,
                event.capabilityName() != null ? event.capabilityName() : event.bindingName(),
                event.outcome().name(),
                CbrOutcome.adjustConfidence(null, confidence, CbrOutcome.DEFAULT_LEARNING_RATE),
                features,
                null,
                event.workerName());

        String entityId = event.caseId().toString() + ":" + event.bindingName();
        String storedId = cbrStore.store(cbrCase, FeatureVectorCbrCase.CBR_TYPE, entityId,
                new MemoryDomain("fsitrading"), event.tenancyId(),
                event.caseId().toString(), Path.root());

        cbrStore.recordOutcome(storedId, event.tenancyId(),
                CbrOutcome.of(confidence, event.outcome().name(), Instant.now()));
    }
}
