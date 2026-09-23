package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.cbr.FsiFeatureExtractor;
import io.casehub.fsitrading.app.resource.PrecedentRecord;
import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.spi.IncidentStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.ResolvedCase;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class SimilarIncidentService {

    private final CbrCaseMemoryStore cbrStore;
    private final IncidentStore incidentStore;
    private final FsiFeatureExtractor featureExtractor;

    @Inject
    public SimilarIncidentService(CbrCaseMemoryStore cbrStore,
                                  IncidentStore incidentStore,
                                  FsiFeatureExtractor featureExtractor) {
        this.cbrStore = cbrStore;
        this.incidentStore = incidentStore;
        this.featureExtractor = featureExtractor;
    }

    public List<PrecedentRecord> findSimilar(String caseId, String tenantId) {
        if (caseId == null || caseId.isBlank()) return List.of();
        UUID parsedId;
        try {
            parsedId = UUID.fromString(caseId);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        IncidentRecord incident = incidentStore.findByCaseId(parsedId);
        if (incident == null) return List.of();

        Map<String, Object> snapshot = buildSnapshot(incident);
        Map<String, Object> rawFeatures = featureExtractor.extractFromSnapshot(
                snapshot, incident.createdAt());
        Map<String, FeatureValue> features = FeatureValue.toFeatureMap(rawFeatures);

        CbrQuery query = CbrQuery.of(tenantId, new MemoryDomain("fsitrading"),
                        Path.root(), ResolvedCase.CBR_TYPE, features, 5)
                .withMinSimilarity(0.3);
        return cbrStore.retrieveSimilar(query, ResolvedCase.class).stream()
                .map(PrecedentRecord::from)
                .toList();
    }

    private static Map<String, Object> buildSnapshot(IncidentRecord incident) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("instrument", incident.instruments().isEmpty()
                ? "UNKNOWN" : incident.instruments().getFirst());
        snapshot.put("eventType", incident.eventType().name());
        snapshot.put("sector", "EQUITY");
        snapshot.put("severity", incident.severity().name());
        snapshot.put("detectedAt", incident.createdAt().toString());
        return snapshot;
    }
}
