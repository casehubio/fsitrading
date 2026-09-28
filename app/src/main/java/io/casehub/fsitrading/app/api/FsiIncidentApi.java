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
import jakarta.ws.rs.QueryParam;

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
    public Object listIncidents(@QueryParam("limit") int limit) {
        return incidentResource.list(limit);
    }

    @PlatformQuery("Get incident details")
    @RestPath("/{caseId}")
    public Object getIncident(@PathParam UUID caseId) {
        return incidentResource.get(caseId);
    }

    @PlatformQuery("Get incident timeline")
    @RestPath("/{caseId}/timeline")
    public Object getTimeline(@PathParam UUID caseId) {
        return incidentResource.timeline(caseId);
    }

    @PlatformQuery("Get incident severity summary")
    @RestPath("/summary/severity")
    public Object summarySeverity() {
        return incidentResource.summarySeverity();
    }

    @PlatformQuery("List work items")
    @RestPath("/work-items")
    public Object listWorkItems(@QueryParam("type") String type,
                                 @QueryParam("status") String status) {
        return workItemResource.list(type, status);
    }

    @PlatformMutation("Resolve a work item")
    @RestPath("/work-items/{id}/resolve")
    public Object resolveWorkItem(@PathParam UUID id, WorkItemResource.ResolveRequest request) {
        return workItemResource.resolve(id, request).getEntity();
    }

    @PlatformQuery("Get decision narrative for an incident")
    @RestPath("/{caseId}/narrative")
    public Object getNarrative(@PathParam String caseId) {
        return narrativeResource.get(caseId);
    }

    @PlatformQuery("Get post-mortem for an incident")
    @RestPath("/{caseId}/postmortem")
    public Object getPostMortem(@PathParam UUID caseId) {
        return postMortemResource.getPostMortem(caseId).getEntity();
    }
}
