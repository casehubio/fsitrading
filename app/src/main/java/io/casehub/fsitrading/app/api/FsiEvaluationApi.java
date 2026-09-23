package io.casehub.fsitrading.app.api;

import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.fsitrading.app.arena.ArenaContext;
import io.casehub.fsitrading.app.model.ArenaRunEntity;
import io.casehub.fsitrading.app.resource.ArenaRunRepository;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@McpDomain(value = "fsi/evaluations", basePath = "/api/fsi/evaluations")
@ApplicationScoped
public class FsiEvaluationApi {

    private static final Logger log = Logger.getLogger(FsiEvaluationApi.class);

    @Inject ExecutionModel<ArenaContext> arenaModel;
    @Inject ArenaRunRepository runRepository;
    @Inject CaseMemoryStore memoryStore;

    @PlatformMutation("Trigger an arena evaluation run")
    @RestPath("/trigger")
    public ArenaResult trigger(TriggerRequest request) {
        var run = new ArenaRunEntity(request.instrument());
        try {
            runRepository.persist(run);
        } catch (PersistenceException e) {
            throw new jakarta.ws.rs.WebApplicationException(
                    "Arena run already in flight for instrument " + request.instrument(), 409);
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
                    .await().atMost(Duration.ofMinutes(5));

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
            return result;
        } catch (Exception e) {
            log.errorf(e, "Arena run failed for %s", request.instrument());
            runRepository.fail(run, e.getMessage());
            throw new jakarta.ws.rs.WebApplicationException(
                    "Arena run failed: " + (e.getMessage() != null ? e.getMessage() : "unknown error"), 500);
        }
    }

    private void emitMemory(ArenaContext ctx) {
        try {
            var signal = ctx.marketSignal();
            String text = String.format("Arena run for %s: %s at %s, consensus=%s, risk=%s",
                    signal.instrument(), signal.eventType(), signal.price(),
                    ctx.consensus() != null ? ctx.consensus().instruments().size() + " instruments" : "none",
                    ctx.riskAssessment() != null ? ctx.riskAssessment().level() : "none");

            memoryStore.store(MemoryInput.of(
                    "arena:" + signal.instrument(),
                    new MemoryDomain("agent"),
                    "fsitrading",
                    text)
                    .withCaseId(ctx.runId().toString())
                    .withAttributes(Map.of("instrument", signal.instrument(),
                            "eventType", signal.eventType(),
                            "runId", ctx.runId().toString())));
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
