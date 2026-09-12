package io.casehub.fsitrading.app.resource;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import io.casehub.neocortex.memory.cbr.ResolvedCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/incidents/history")
@Produces(MediaType.APPLICATION_JSON)
public class IncidentHistoryResource {

    private static final MemoryDomain FSI_DOMAIN = new MemoryDomain("fsitrading");

    private final CbrCaseMemoryStore cbrStore;

    @Inject
    public IncidentHistoryResource(CbrCaseMemoryStore cbrStore) {
        this.cbrStore = cbrStore;
    }

    @GET
    public CbrScanResult history(
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("cursor") String cursor,
            @QueryParam("tenantId") @DefaultValue("default") String tenantId) {
        return cbrStore.scan(new CbrScanRequest(
                tenantId, FSI_DOMAIN, ResolvedCase.CBR_TYPE, limit, cursor));
    }
}
