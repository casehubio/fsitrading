package io.casehub.fsitrading.app.cbr;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.api.spi.CaseOutcomeObserver;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiCaseOutcomeObserver implements CaseOutcomeObserver {

    private static final String CASE_TYPE = "overnight-incident";

    private final CbrCaseMemoryStore cbrStore;
    private final FsiFeatureExtractor featureExtractor;

    @Inject
    public FsiCaseOutcomeObserver(CbrCaseMemoryStore cbrStore,
                                  FsiFeatureExtractor featureExtractor) {
        this.cbrStore = cbrStore;
        this.featureExtractor = featureExtractor;
    }

    @Override
    public void onOutcome(CaseOutcomeEvent event) {
        if (!CASE_TYPE.equals(event.caseType())) return;
        if (!"COMPLETED".equals(event.outcomeLabel())) return;

        Map<String, Object> snapshot = event.caseFileSnapshot();
        String detectedAt = (String) snapshot.get("detectedAt");
        Instant detection = Instant.parse(detectedAt);

        Map<String, Object> rawFeatures =
                featureExtractor.extractFromSnapshot(snapshot, detection);
        Map<String, FeatureValue> features = FeatureValue.toFeatureMap(rawFeatures);

        String eventType = (String) snapshot.get("eventType");
        String severity = (String) snapshot.get("severity");
        String instrument = (String) snapshot.get("instrument");

        PlanCbrCase planCase = new PlanCbrCase(
                severity + " " + eventType + " incident on " + instrument,
                "HTN decomposition response",
                event.outcomeLabel(),
                CbrOutcome.adjustConfidence(null, 1.0, CbrOutcome.DEFAULT_LEARNING_RATE),
                features,
                List.of(),
                null,
                "fsi-incident-cbr");

        String caseId = event.caseId().toString();
        String storedId = cbrStore.store(planCase, PlanCbrCase.CBR_TYPE, caseId,
                new MemoryDomain("fsitrading"), event.tenancyId(),
                caseId, Path.root());

        cbrStore.recordOutcome(storedId, event.tenancyId(),
                CbrOutcome.of(1.0, event.outcomeLabel(), Instant.now()));
    }
}
