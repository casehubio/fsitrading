package io.casehub.fsitrading.app.api;

import io.casehub.blocks.summarisation.narrative.DecisionNarrative;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.fsitrading.app.incident.FsiIncidentTrigger;
import io.casehub.fsitrading.model.ExternalIncidentRequest;
import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.IncidentSummary;
import io.casehub.fsitrading.model.IncidentTimelineRecord;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.spi.IncidentStore;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.QueryParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@McpDomain(value = "fsi/incidents", basePath = "/api/fsi/incidents")
@ApplicationScoped
public class FsiIncidentApi {

    private final Map<String, List<DecisionNarrative>> narrativeCache = new ConcurrentHashMap<>();

    @Inject IncidentStore incidentStore;
    @Inject WorkItemStore workItemStore;
    @Inject WorkItemService workItemService;
    @Inject FsiIncidentTrigger trigger;

    @Inject
    void initNarrativeBus(DecisionNarrativePipeline pipeline) {
        pipeline.narrativeBus().subscribe(e -> true, event -> {
            var narrative = event.payload();
            narrativeCache.computeIfAbsent(narrative.caseId(), k -> new CopyOnWriteArrayList<>())
                    .add(narrative);
        });
    }

    @PlatformQuery("List incidents")
    @RestPath("/")
    public List<IncidentRecord> listIncidents(@QueryParam("limit") int limit) {
        return incidentStore.findRecent(limit > 0 ? limit : 20);
    }

    @PlatformQuery("Get incident details")
    @RestPath("/{caseId}")
    public IncidentRecord getIncident(@PathParam UUID caseId) {
        IncidentRecord found = incidentStore.findByCaseId(caseId);
        if (found == null) {
            throw new NotFoundException("Incident not found: " + caseId);
        }
        return found;
    }

    @PlatformQuery("Get incident timeline")
    @RestPath("/{caseId}/timeline")
    public List<IncidentTimelineRecord> getTimeline(@PathParam UUID caseId) {
        return incidentStore.getTimeline(caseId);
    }

    @PlatformQuery("Get incident severity summary")
    @RestPath("/summary/severity")
    public List<IncidentSummary.SeverityCount> summarySeverity() {
        return incidentStore.getSummary().bySeverity();
    }

    @PlatformQuery("List work items")
    @RestPath("/work-items")
    public List<WorkItem> listWorkItems(@QueryParam("type") String type,
                                         @QueryParam("status") String status) {
        var builder = WorkItemQuery.builder();
        if (type != null) {
            builder.type(type);
        }
        if (status != null) {
            builder.status(WorkItemStatus.valueOf(status));
        }
        return workItemStore.scan(builder.build());
    }

    @PlatformMutation("Resolve a work item")
    @RestPath("/work-items/{id}/resolve")
    public WorkItem resolveWorkItem(@PathParam UUID id, ResolveRequest request) {
        if (request.outcome() == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        return switch (request.outcome()) {
            case "APPROVED" -> workItemService.complete(id, request.actorId(),
                    request.resolution(), request.outcome());
            case "REJECTED" -> workItemService.reject(id, request.actorId(),
                    request.resolution(), request.outcome());
            case "DELEGATED" -> workItemService.delegate(id, request.actorId(),
                    request.delegateTo(), null);
            default -> throw new IllegalArgumentException("Unknown outcome: " + request.outcome());
        };
    }

    @PlatformQuery("Get decision narrative for an incident")
    @RestPath("/{caseId}/narrative")
    public List<DecisionNarrative> getNarrative(@PathParam String caseId) {
        return narrativeCache.getOrDefault(caseId, List.of());
    }

    @PlatformQuery("Get post-mortem for an incident")
    @RestPath("/{caseId}/postmortem")
    public String getPostMortem(@PathParam UUID caseId) {
        throw new NotFoundException(
                "Post-mortem generation requires qhorus channel replay — not yet wired");
    }

    @PlatformQuery("Get incident status summary")
    @RestPath("/summary/status")
    public Map<String, Object> summaryStatus() {
        var summary = incidentStore.getSummary();
        return Map.of(
                "totalActive", summary.totalActive(),
                "slaStatus", summary.slaStatus());
    }

    @PlatformMutation("Simulate an incident")
    @RestPath("/simulate")
    public UUID simulate(SimulateRequest request) {
        return trigger.triggerSimulated(
                request.severity(), request.eventType(),
                request.instruments(), request.description());
    }

    @PlatformMutation("Report an external incident")
    @RestPath("/external")
    public UUID external(ExternalIncidentRequest request) {
        return trigger.triggerFromExternal(request);
    }

    public record ResolveRequest(String actorId, String outcome,
                                  String resolution, String delegateTo) {}

    public record SimulateRequest(IncidentSeverity severity, MarketEventType eventType,
                                   List<String> instruments, String description) {}
}
