package io.casehub.fsitrading.app.resource;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Produces(MediaType.APPLICATION_JSON)
@io.casehub.platform.api.mcp.HandWrittenEndpoint("pending @McpDomain migration")
@Path("/api/incidents/history")
public class IncidentHistoryResource  {

    private static final MemoryDomain FSI_DOMAIN = new MemoryDomain("fsitrading");

    private final CbrRecordStore cbrStore;

    @Inject
    public IncidentHistoryResource(CbrRecordStore cbrStore) {
        this.cbrStore = cbrStore;
    }

    @GET
    public CbrScanResult history(
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("cursor") String cursor,
            @QueryParam("tenantId") @DefaultValue("default") String tenantId) {
        return cbrStore.scan(new CbrScanRequest(
                tenantId, FSI_DOMAIN, CbrPlanRecord.CBR_TYPE, limit, cursor));
    }
}
