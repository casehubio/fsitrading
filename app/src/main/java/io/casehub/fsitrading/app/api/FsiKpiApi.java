package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.resource.PrecedentRecord;
import io.casehub.fsitrading.app.service.SimilarIncidentService;
import io.casehub.fsitrading.app.service.PositionService;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import io.casehub.neocortex.memory.cbr.ResolvedCase;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@McpDomain(value = "fsi/kpis", basePath = "/api/fsi/kpis")
@ApplicationScoped
public class FsiKpiApi {

    private static final MemoryDomain FSI_DOMAIN = new MemoryDomain("fsitrading");

    @Inject PositionService positionService;
    @Inject StrategyService strategyService;
    @Inject CbrCaseMemoryStore cbrStore;
    @Inject SimilarIncidentService similarIncidentService;

    @PlatformQuery("Get KPI dashboard metrics")
    @RestPath("/")
    public KpiSummary getKpis() {
        var positions = positionService.findAll();
        long tradeCount = positions.stream()
                .filter(p -> p.getRealizedPnl().signum() != 0)
                .count();
        BigDecimal totalPnl = positions.stream()
                .map(PositionEntity::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long winCount = positions.stream()
                .filter(p -> p.getRealizedPnl().signum() > 0)
                .count();
        double winRate = tradeCount > 0 ? (double) winCount / tradeCount : 0.0;
        BigDecimal avgReturn = tradeCount > 0
                ? totalPnl.divide(BigDecimal.valueOf(tradeCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new KpiSummary(totalPnl, winRate, tradeCount, avgReturn);
    }

    @PlatformQuery("Get alert heatmap data")
    @RestPath("/heatmap")
    public List<HeatmapCell> getHeatmap() {
        var positions = positionService.findAll();
        return positions.stream()
                .filter(p -> p.getRealizedPnl().signum() != 0)
                .map(p -> {
                    var strategy = strategyService.findById(p.getStrategyId());
                    String strategyName = strategy != null ? strategy.getName() : p.getStrategyId().toString();
                    return new HeatmapCell(p.getInstrument(), strategyName, p.getRealizedPnl());
                })
                .toList();
    }

    @PlatformQuery("Get incident history")
    @RestPath("/incident-history")
    public CbrScanResult getIncidentHistory() {
        return cbrStore.scan(new CbrScanRequest(
                "default", FSI_DOMAIN, ResolvedCase.CBR_TYPE, 20, null));
    }

    @PlatformQuery("Find similar past incidents")
    @RestPath("/similar-incidents")
    public List<PrecedentRecord> findSimilarIncidents(@QueryParam("caseId") UUID caseId) {
        return similarIncidentService.findSimilar(
                caseId != null ? caseId.toString() : null, "default");
    }

    public record KpiSummary(BigDecimal totalPnl, double winRate, long tradeCount, BigDecimal avgReturn) {}

    public record HeatmapCell(String instrument, String strategy, BigDecimal pnl) {}
}
