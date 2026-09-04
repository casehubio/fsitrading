package io.casehub.fsitrading.app.resource;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.api.path.Path;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@jakarta.ws.rs.Path("/api/incidents/similar")
@Produces(MediaType.APPLICATION_JSON)
public class SimilarIncidentResource {

    private final CbrCaseMemoryStore cbrStore;

    @Inject
    public SimilarIncidentResource(CbrCaseMemoryStore cbrStore) {
        this.cbrStore = cbrStore;
    }

    @GET
    public List<PrecedentRecord> findSimilar(
            @QueryParam("caseId") String caseId,
            @QueryParam("tenantId") @DefaultValue("default") String tenantId) {
        CbrQuery query = CbrQuery.of(tenantId, new MemoryDomain("fsitrading"),
                                     Path.root(), PlanCbrCase.CBR_TYPE, Map.of(), 5)
                                 .withMinSimilarity(0.3);
        return cbrStore.retrieveSimilar(query, PlanCbrCase.class).stream()
                       .map(PrecedentRecord::from)
                       .toList();
    }
}
