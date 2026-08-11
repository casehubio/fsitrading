package io.casehub.fsitrading.app.resource;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.fsitrading.app.arena.ArenaContext;
import io.casehub.fsitrading.app.model.ArenaRunEntity;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/evaluations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EvaluationResource {

    private static final Logger log = Logger.getLogger(EvaluationResource.class);

    @Inject
    ExecutionModel<ArenaContext> arenaModel;

    @Inject
    ArenaRunRepository runRepository;

    @Inject
    CaseMemoryStore memoryStore;

    @POST
    @Path("/trigger")
    public Response trigger(TriggerRequest request,
                            @HeaderParam("Idempotency-Key") UUID idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = runRepository.findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                if (existing.isInFlight()) {
                    return Response.status(409).entity(Map.of(
                            "error", "in_flight",
                            "runId", String.valueOf(existing.getId()),
                            "message", "Arena run already in flight for this idempotency key")).build();
                }
                return Response.ok(existing.getResultJson()).build();
            }
        }

        var run = new ArenaRunEntity(request.instrument());
        if (idempotencyKey != null) {
            run.setIdempotencyKey(idempotencyKey);
        }
        try {
            runRepository.persist(run);
        } catch (PersistenceException e) {
            return Response.status(409).entity(Map.of(
                    "error", "concurrent",
                    "message", "Arena run already in flight for instrument " + request.instrument())).build();
        }

        var signal = new MarketSignal(
                request.instrument(), request.eventType(),
                request.price(), request.volume(), Instant.now());
        var ctx = new ArenaContext(signal);

        try {
            var backend = arenaModel.backend() != null
                    ? arenaModel.backend()
                    : io.casehub.blocks.agentic.model.ExecutionBackend.<ArenaContext>reactive();
            backend.execute(arenaModel, ctx)
                    .await().atMost(java.time.Duration.ofMinutes(5));

            var selectedNames = ctx.selectedAgents() != null
                    ? ctx.selectedAgents().stream().map(c -> c.ref().name()).toList()
                    : List.<String>of();

            var result = new ArenaResult(
                    ctx.runId(), signal, selectedNames,
                    ctx.evaluations(), ctx.consensus(),
                    ctx.riskAssessment(),
                    ctx.approvalOutcome() != null ? ctx.approvalOutcome().name() : null);

            runRepository.complete(run, toJson(result));
            emitMemory(ctx);

            return Response.ok(result).build();
        } catch (Exception e) {
            log.errorf(e, "Arena run failed for %s", request.instrument());
            runRepository.fail(run, e.getMessage());
            return Response.serverError().entity(Map.of(
                    "error", "arena_failed",
                    "runId", run.getId(),
                    "message", e.getMessage() != null ? e.getMessage() : "unknown error")).build();
        }
    }

    private void emitMemory(ArenaContext ctx) {
        try {
            var signal = ctx.marketSignal();
            String text = String.format("Arena run for %s: %s at %s, consensus=%s, risk=%s",
                    signal.instrument(), signal.eventType(), signal.price(),
                    ctx.consensus() != null ? ctx.consensus().instruments().size() + " instruments" : "none",
                    ctx.riskAssessment() != null ? ctx.riskAssessment().level() : "none");

            memoryStore.store(new MemoryInput(
                    "arena:" + signal.instrument(),
                    new MemoryDomain("agent"),
                    "fsitrading",
                    ctx.runId().toString(),
                    text,
                    Map.of("instrument", signal.instrument(),
                            "eventType", signal.eventType(),
                            "runId", ctx.runId().toString()),
                    0.7));
        } catch (Exception e) {
            log.warnf(e, "Memory emission failed for arena run %s", ctx.runId());
        }
    }

    private String toJson(ArenaResult result) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .findAndRegisterModules()
                    .writeValueAsString(result);
        } catch (Exception e) {
            log.errorf(e, "Failed to serialize ArenaResult for run %s", result.runId());
            return "{}";
        }
    }

    public record TriggerRequest(String instrument, String eventType,
                                  BigDecimal price, BigDecimal volume) {}
}
