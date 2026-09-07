package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.cbr.FsiFeatureExtractor;
import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.spi.IncidentStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.api.path.Path;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@jakarta.ws.rs.Path("/api/incidents/similar")
@Produces(MediaType.APPLICATION_JSON)
public class SimilarIncidentResource {

    private final CbrCaseMemoryStore cbrStore;
    private final IncidentStore incidentStore;
    private final FsiFeatureExtractor featureExtractor;

    @Inject
    public SimilarIncidentResource(CbrCaseMemoryStore cbrStore,
                                   IncidentStore incidentStore,
                                   FsiFeatureExtractor featureExtractor) {
        this.cbrStore = cbrStore;
        this.incidentStore = incidentStore;
        this.featureExtractor = featureExtractor;
    }

    @GET
    public List<PrecedentRecord> findSimilar(
            @QueryParam("caseId") String caseId,
            @QueryParam("tenantId") @DefaultValue("default") String tenantId) {
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
                        Path.root(), PlanCbrCase.CBR_TYPE, features, 5)
                .withMinSimilarity(0.3);
        return cbrStore.retrieveSimilar(query, PlanCbrCase.class).stream()
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
