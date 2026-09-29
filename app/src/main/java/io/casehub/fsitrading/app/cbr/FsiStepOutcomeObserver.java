package io.casehub.fsitrading.app.cbr;

import io.casehub.api.spi.StepOutcomeEvent;
import io.casehub.api.spi.StepOutcomeObserver;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class FsiStepOutcomeObserver implements StepOutcomeObserver {

    private final CbrRecordStore              cbrStore;
    private final FsiFeatureExtractorRegistry extractorRegistry;

    @Inject
    public FsiStepOutcomeObserver(CbrRecordStore cbrStore,
                                  FsiFeatureExtractorRegistry extractorRegistry) {
        this.cbrStore          = cbrStore;
        this.extractorRegistry = extractorRegistry;
    }

    @Override
    public void onStepOutcome(StepOutcomeEvent event) {
        Map<String, Object> snapshot   = event.contextSnapshot();
        String              detectedAt = (String) snapshot.get("detectedAt");
        if (detectedAt == null) {return;}
        Instant detection = Instant.parse(detectedAt);

        var rawFeatures = extractorRegistry.extractFeatures(
                event.caseType(), snapshot, detection);
        if (rawFeatures.isEmpty()) {return;}
        Map<String, FeatureValue> features = FeatureValue.toFeatureMap(rawFeatures.get());

        String problem    = event.bindingName() + " executed by " + event.workerName();
        double confidence = event.outcome().name().equals("SUCCESS") ? 1.0 : 0.0;

        CbrFeatureRecord cbrCase = new CbrFeatureRecord(
                problem,
                event.capabilityName() != null ? event.capabilityName() : event.bindingName(),
                event.outcome().name(),
                CbrOutcome.adjustConfidence(null, confidence, CbrOutcome.DEFAULT_LEARNING_RATE),
                features,
                null,
                event.workerName());

        String entityId = event.caseId().toString() + ":" + event.bindingName();
        String storedId = cbrStore.store(cbrCase, CbrFeatureRecord.CBR_TYPE, entityId,
                                         new MemoryDomain("fsitrading"), event.tenancyId(),
                                         event.caseId().toString(), Path.root());

        cbrStore.recordOutcome(storedId, event.tenancyId(),
                               CbrOutcome.of(confidence, event.outcome().name(), Instant.now()));
    }
}
