package io.casehub.fsitrading.app.cbr;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.api.spi.CaseOutcomeObserver;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiCaseOutcomeObserver implements CaseOutcomeObserver {

    private final CbrRecordStore              cbrStore;
    private final FsiFeatureExtractorRegistry extractorRegistry;

    @Inject
    public FsiCaseOutcomeObserver(CbrRecordStore cbrStore,
                                  FsiFeatureExtractorRegistry extractorRegistry) {
        this.cbrStore          = cbrStore;
        this.extractorRegistry = extractorRegistry;
    }

    @Override
    public void onOutcome(CaseOutcomeEvent event) {
        if (!"COMPLETED".equals(event.outcomeLabel())) {return;}

        Map<String, Object> snapshot   = event.caseFileSnapshot();
        String              detectedAt = (String) snapshot.get("detectedAt");
        if (detectedAt == null) {return;}
        Instant detection = Instant.parse(detectedAt);

        var rawFeatures = extractorRegistry.extractFeatures(
                event.caseType(), snapshot, detection);
        if (rawFeatures.isEmpty()) {return;}
        Map<String, FeatureValue> features = FeatureValue.toFeatureMap(rawFeatures.get());

        String eventType  = (String) snapshot.get("eventType");
        String severity   = (String) snapshot.get("severity");
        String instrument = (String) snapshot.get("instrument");

        CbrPlanRecord cbrCase = new CbrPlanRecord(
                severity + " " + eventType + " incident on " + instrument,
                "HTN decomposition response",
                event.outcomeLabel(),
                CbrOutcome.adjustConfidence(null, 1.0, CbrOutcome.DEFAULT_LEARNING_RATE),
                features,
                List.of(),
                null,
                "fsi-incident-cbr");

        String caseId = event.caseId().toString();
        String storedId = cbrStore.store(cbrCase, CbrPlanRecord.CBR_TYPE, caseId,
                                         new MemoryDomain("fsitrading"), event.tenancyId(),
                                         caseId, Path.root());

        cbrStore.recordOutcome(storedId, event.tenancyId(),
                               CbrOutcome.of(1.0, event.outcomeLabel(), Instant.now()));
    }
}
