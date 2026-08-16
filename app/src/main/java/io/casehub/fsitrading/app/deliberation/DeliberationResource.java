package io.casehub.fsitrading.app.deliberation;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/deliberations")
@Produces(MediaType.APPLICATION_JSON)
public class DeliberationResource {

    @Inject
    DeliberationRecordRepository repository;

    @Inject
    FsiDeliberationOrchestrator orchestrator;

    @GET
    public List<DeliberationRecord> list(@QueryParam("instrument") String instrument,
                                          @QueryParam("convergenceState") String convergenceState,
                                          @QueryParam("triggerType") String triggerType) {
        if (instrument != null && convergenceState != null) {
            return repository.findByInstrumentAndStatus(instrument, convergenceState);
        }
        return repository.findAll();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") UUID id) {
        return repository.findById(id)
                .map(r -> Response.ok(r).build())
                .orElse(Response.status(404).build());
    }

    @POST
    @Path("/trigger")
    public Response manualTrigger(@QueryParam("instrument") String instrument) {
        if (instrument == null || instrument.isBlank()) {
            return Response.status(400).entity("instrument required").build();
        }
        if (repository.findInProgress(instrument).isPresent()) {
            return Response.status(409).entity("Deliberation already in progress for " + instrument).build();
        }
        var recordId = orchestrator.startDeliberation(instrument, "MANUAL", List.of());
        return Response.accepted(recordId).build();
    }
}
