package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.EvaluationResource;
import io.casehub.fsitrading.app.resource.IncidentHistoryResource;
import io.casehub.fsitrading.app.resource.KpiResource.HeatmapCell;
import io.casehub.fsitrading.app.resource.KpiResource.KpiSummary;
import io.casehub.fsitrading.app.resource.KpiResource;
import io.casehub.fsitrading.app.resource.PrecedentRecord;
import io.casehub.fsitrading.app.resource.SimilarIncidentResource;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "fsi/kpis", basePath = "/api/fsi/kpis")
@ApplicationScoped
public class FsiKpiApi {

    @Inject KpiResource kpiResource;
    @Inject EvaluationResource evaluationResource;
    @Inject IncidentHistoryResource incidentHistoryResource;
    @Inject SimilarIncidentResource similarIncidentResource;

    @PlatformQuery("Get KPI dashboard metrics")
    @RestPath("/summary")
    public KpiSummary getKpis() {
        return kpiResource.getKpis();
    }

    @PlatformQuery("Get strategy-instrument heatmap")
    @RestPath("/heatmap")
    public List<HeatmapCell> getHeatmap() {
        return kpiResource.getHeatmap();
    }

    @PlatformQuery("Get incident history")
    @RestPath("/incident-history")
    public CbrScanResult getIncidentHistory() {
        return incidentHistoryResource.history(20, null, "default");
    }

    @PlatformQuery("Find similar incidents")
    @RestPath("/similar/{caseId}")
    public List<PrecedentRecord> findSimilarIncidents(@PathParam UUID caseId) {
        return similarIncidentResource.findSimilar(caseId.toString(), "default");
    }
}
