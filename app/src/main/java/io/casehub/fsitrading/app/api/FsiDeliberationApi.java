package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.deliberation.DeliberationRecord;
import io.casehub.fsitrading.app.deliberation.DeliberationRecordRepository;
import io.casehub.fsitrading.app.deliberation.FsiDeliberationOrchestrator;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "fsi/deliberations", basePath = "/api/fsi/deliberations")
@ApplicationScoped
public class FsiDeliberationApi {

    @Inject
    DeliberationRecordRepository repository;

    @Inject
    FsiDeliberationOrchestrator orchestrator;

    @PlatformQuery("List deliberation records")
    @RestPath("/")
    public List<DeliberationRecord> list(@QueryParam("instrument") String instrument,
                                         @QueryParam("convergenceState") String convergenceState) {
        if (instrument != null && convergenceState != null) {
            return repository.findByInstrumentAndStatus(instrument, convergenceState);
        }
        return repository.findAll();
    }

    @PlatformQuery("Get deliberation by ID")
    @RestPath("/{id}")
    public DeliberationRecord getById(@PathParam UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Deliberation not found: " + id));
    }

    @PlatformMutation("Trigger a manual deliberation")
    @RestPath("/trigger")
    public UUID manualTrigger(@QueryParam("instrument") String instrument) {
        if (instrument == null || instrument.isBlank()) {
            throw new IllegalArgumentException("instrument required");
        }
        if (repository.findInProgress(instrument).isPresent()) {
            throw new jakarta.ws.rs.WebApplicationException(
                    "Deliberation already in progress for " + instrument, 409);
        }
        return orchestrator.startDeliberation(instrument, "MANUAL", List.of());
    }
}
