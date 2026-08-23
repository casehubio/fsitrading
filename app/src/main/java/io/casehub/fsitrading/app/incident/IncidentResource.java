package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.model.ExternalIncidentRequest;
import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.IncidentSummary;
import io.casehub.fsitrading.model.IncidentTimelineRecord;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.spi.IncidentStore;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/incidents")
@Produces(MediaType.APPLICATION_JSON)
public class IncidentResource {

    @Inject
    IncidentStore store;

    @Inject
    FsiIncidentTrigger trigger;

    @GET
    public List<IncidentRecord> list(@QueryParam("limit") @DefaultValue("20") int limit) {
        return store.findRecent(limit);
    }

    @GET
    @Path("/{caseId}")
    public IncidentRecord get(@PathParam("caseId") UUID caseId) {
        IncidentRecord found = store.findByCaseId(caseId);
        if (found == null) {
            throw new jakarta.ws.rs.NotFoundException("Incident not found: " + caseId);
        }
        return found;
    }

    @GET
    @Path("/{caseId}/timeline")
    public List<IncidentTimelineRecord> timeline(@PathParam("caseId") UUID caseId) {
        return store.getTimeline(caseId);
    }

    @GET
    @Path("/summary/severity")
    public List<IncidentSummary.SeverityCount> summarySeverity() {
        return store.getSummary().bySeverity();
    }

    @GET
    @Path("/summary/status")
    public List<Map<String, Object>> summaryStatus() {
        var summary = store.getSummary();
        return List.of(Map.of(
                "totalActive", summary.totalActive(),
                "slaStatus", summary.slaStatus()));
    }


    @POST
    @Path("/simulate")
    public Response simulate(SimulateRequest request) {
        UUID caseId = trigger.triggerSimulated(
                request.severity(), request.eventType(),
                request.instruments(), request.description());
        return Response.status(Response.Status.CREATED)
                       .entity(Map.of("caseId", caseId))
                       .build();
    }

    @POST
    @Path("/external")
    @RolesAllowed("fsi-ops")
    public Response external(ExternalIncidentRequest request) {
        UUID caseId = trigger.triggerFromExternal(request);
        return Response.status(Response.Status.CREATED)
                       .entity(Map.of("caseId", caseId))
                       .build();
    }

    public record SimulateRequest(
            IncidentSeverity severity,
            MarketEventType eventType,
            List<String> instruments,
            String description) {}
}
