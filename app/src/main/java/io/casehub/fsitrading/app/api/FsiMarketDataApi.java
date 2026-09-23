package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.app.model.OhlcvBarEntity;
import io.casehub.fsitrading.app.model.TrendSummaryEntity;
import io.casehub.fsitrading.app.pipeline.FsiObservationCache;
import io.casehub.fsitrading.app.pipeline.MarketPulseScheduler;
import io.casehub.fsitrading.app.service.ScenarioRunner;
import io.casehub.fsitrading.app.service.SyntheticMarketDataProvider;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.ScenarioType;
import io.casehub.fsitrading.model.SessionNarrative;
import io.casehub.fsitrading.app.resource.ScenarioRequest;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.QueryParam;

import java.util.List;

@McpDomain(value = "fsi/market-data", basePath = "/api/fsi/market-data")
@ApplicationScoped
public class FsiMarketDataApi {

    @Inject SyntheticMarketDataProvider marketDataProvider;
    @Inject ScenarioRunner scenarioRunner;
    @Inject FsiObservationCache observationCache;
    @Inject MarketPulseScheduler scheduler;
    @Inject EntityManager em;

    @PlatformMutation("Generate a synthetic price tick")
    @RestPath("/tick")
    public PriceTick generateTick() {
        return marketDataProvider.generateTick();
    }

    @PlatformQuery("Get recent market events")
    @RestPath("/recent")
    public List<MarketEventEntity> recent(@QueryParam("limit") int limit) {
        return marketDataProvider.findRecent(limit > 0 ? limit : 20);
    }

    @PlatformQuery("Get OHLCV bars for an instrument")
    @RestPath("/bars/{instrument}")
    public List<OhlcvBarEntity> bars(@PathParam String instrument, @QueryParam("limit") int limit) {
        return em.createQuery(
                        "SELECT b FROM OhlcvBarEntity b WHERE b.instrument = :instrument ORDER BY b.windowStart DESC",
                        OhlcvBarEntity.class)
                .setParameter("instrument", instrument)
                .setMaxResults(limit > 0 ? limit : 60)
                .getResultList();
    }

    @PlatformQuery("Get trend summaries for an instrument")
    @RestPath("/trends/{instrument}")
    public List<TrendSummaryEntity> trends(@PathParam String instrument, @QueryParam("limit") int limit) {
        return em.createQuery(
                        "SELECT t FROM TrendSummaryEntity t WHERE t.instrument = :instrument ORDER BY t.windowStart DESC",
                        TrendSummaryEntity.class)
                .setParameter("instrument", instrument)
                .setMaxResults(limit > 0 ? limit : 20)
                .getResultList();
    }

    @PlatformQuery("Get regime assessment for an instrument")
    @RestPath("/regime/{instrument}")
    public RegimeAssessment regime(@PathParam String instrument) {
        return observationCache.latestRegime(instrument).orElse(null);
    }

    @PlatformQuery("Get session narrative")
    @RestPath("/narrative")
    public SessionNarrative narrative() {
        return observationCache.latestNarrative().orElse(null);
    }

    @PlatformMutation("Run a market scenario")
    @RestPath("/scenario")
    public ScenarioResult scenario(ScenarioRequest request) {
        List<PriceTick> ticks = scenarioRunner.generate(request.scenarioType());
        return new ScenarioResult(request.scenarioType(), ticks.size());
    }

    @PlatformMutation("Pause the market data scheduler")
    @RestPath("/scheduler/pause")
    public SchedulerStatus pauseScheduler() {
        scheduler.pause();
        return new SchedulerStatus(true);
    }

    @PlatformMutation("Resume the market data scheduler")
    @RestPath("/scheduler/resume")
    public SchedulerStatus resumeScheduler() {
        scheduler.resume();
        return new SchedulerStatus(false);
    }

    public record ScenarioResult(ScenarioType scenarioType, int tickCount) {}

    public record SchedulerStatus(boolean paused) {}
}
