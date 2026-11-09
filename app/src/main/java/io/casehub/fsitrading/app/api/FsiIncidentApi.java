package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.incident.IncidentResource;
import io.casehub.fsitrading.app.incident.WorkItemResource;
import io.casehub.fsitrading.app.narrative.NarrativeResource;
import io.casehub.fsitrading.app.postmortem.PostMortemResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@McpDomain(value = "fsi/incidents", basePath = "/api/fsi/incidents")
@ApplicationScoped
public class FsiIncidentApi {

    @Inject IncidentResource incidentResource;
    @Inject WorkItemResource workItemResource;
    @Inject NarrativeResource narrativeResource;
    @Inject PostMortemResource postMortemResource;

    @PlatformQuery("List incidents")
    @RestPath("/")
    public List<IncidentRecord> listIncidents(Integer limit) {
        return incidentResource.list(limit != null ? limit : 20);
    }

    @PlatformQuery("Get an incident by case ID")
    @RestPath("/{caseId}")
    public IncidentRecord getIncident(@PathParam UUID caseId) {
        return incidentResource.get(caseId);
    }

    @PlatformQuery("Get incident timeline")
    @RestPath("/{caseId}/timeline")
    public List<IncidentTimelineRecord> getTimeline(@PathParam UUID caseId) {
        return incidentResource.timeline(caseId);
    }

    @PlatformQuery("Get incident severity summary")
    @RestPath("/summary/severity")
    public List<IncidentSummary.SeverityCount> summarySeverity() {
        return incidentResource.summarySeverity();
    }

    @PlatformQuery("List work items")
    @RestPath("/work-items")
    public List<WorkItem> listWorkItems(String type, String status) {
        return workItemResource.list(type, null, status);
    }

    @PlatformMutation("Resolve a work item")
    @RestPath("/work-items/{id}/resolve")
    public WorkItem resolveWorkItem(@PathParam UUID id, ResolveRequest request) {
        return (WorkItem) workItemResource.resolve(id, request).getEntity();
    }

    @PlatformQuery("Get decision narratives for a case")
    @RestPath("/{caseId}/narrative")
    public List<DecisionNarrative> getNarrative(String caseId) {
        return narrativeResource.get(caseId);
    }

}
