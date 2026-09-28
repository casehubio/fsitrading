package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.EvaluationResource;
import io.casehub.fsitrading.app.resource.IncidentHistoryResource;
import io.casehub.fsitrading.app.resource.KpiResource;
import io.casehub.fsitrading.app.resource.SimilarIncidentResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.util.UUID;

@McpDomain(value = "fsi/kpis", basePath = "/api/fsi/kpis")
@ApplicationScoped
public class FsiKpiApi {

    @Inject KpiResource kpiResource;
    @Inject EvaluationResource evaluationResource;
    @Inject IncidentHistoryResource incidentHistoryResource;
    @Inject SimilarIncidentResource similarIncidentResource;

    @PlatformQuery("Get KPI dashboard metrics")
    @RestPath("/")
    public Object getKpis() {
        return kpiResource.getKpis();
    }

    @PlatformQuery("Get alert heatmap data")
    @RestPath("/heatmap")
    public Object getHeatmap() {
        return kpiResource.getHeatmap();
    }

    @PlatformQuery("Get incident history")
    @RestPath("/incident-history")
    public Object getIncidentHistory() {
        return incidentHistoryResource.list();
    }

    @PlatformQuery("Find similar past incidents")
    @RestPath("/similar-incidents")
    public Object findSimilarIncidents(@QueryParam("caseId") UUID caseId) {
        return similarIncidentResource.findSimilar(caseId);
    }
}
