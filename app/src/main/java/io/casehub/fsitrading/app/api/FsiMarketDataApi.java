package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.MarketDataResource;
import io.casehub.fsitrading.app.view.MarketEventView;
import io.casehub.fsitrading.app.view.OhlcvBarView;
import io.casehub.fsitrading.app.view.ScenarioResultView;
import io.casehub.fsitrading.app.view.SchedulerStateView;
import io.casehub.fsitrading.app.view.TrendSummaryView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpDomain(value = "fsi/market-data", basePath = "/api/fsi/market-data")
@ApplicationScoped
public class FsiMarketDataApi {

    @Inject MarketDataResource resource;

    @PlatformMutation("Generate a simulated market tick")
    @RestPath("/tick")
    public PriceTick generateTick() {
        return resource.generateTick();
    }

    @PlatformQuery("Get recent market events")
    @RestPath("/recent")
    public List<MarketEventView> recent(Integer limit) {
        return resource.recent(limit).stream().map(MarketEventView::toView).toList();
    }

    @PlatformQuery("Get OHLCV bars for an instrument")
    @RestPath("/bars")
    public List<OhlcvBarView> bars(String instrument, Integer limit) {
        return resource.bars(instrument, limit).stream().map(OhlcvBarView::toView).toList();
    }

    @PlatformQuery("Get trend summaries for an instrument")
    @RestPath("/trends")
    public List<TrendSummaryView> trends(String instrument, Integer limit) {
        return resource.trends(instrument, limit).stream().map(TrendSummaryView::toView).toList();
    }

    @PlatformQuery("Get regime assessment for an instrument")
    @RestPath("/regime")
    public RegimeAssessment regime(String instrument) {
        return resource.regime(instrument);
    }

    @PlatformQuery("Get session narrative")
    @RestPath("/narrative")
    public SessionNarrative narrative() {
        return resource.narrative();
    }

    @PlatformMutation("Run a market scenario simulation")
    @RestPath("/scenario")
    @SuppressWarnings("unchecked")
    public ScenarioResultView scenario(ScenarioRequest request) {
        var result = (java.util.Map<String, Object>) resource.scenario(request);
        return new ScenarioResultView(
                String.valueOf(result.get("scenarioType")),
                ((Number) result.get("tickCount")).intValue());
    }

    @PlatformMutation("Pause the market data scheduler")
    @RestPath("/scheduler/pause")
    public SchedulerStateView pauseScheduler() {
        resource.pauseScheduler();
        return new SchedulerStateView(true);
    }

    @PlatformMutation("Resume the market data scheduler")
    @RestPath("/scheduler/resume")
    public SchedulerStateView resumeScheduler() {
        resource.resumeScheduler();
        return new SchedulerStateView(false);
    }
}
