package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.MarketDataResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

@McpDomain(value = "fsi/market-data", basePath = "/api/fsi/market-data")
@ApplicationScoped
public class FsiMarketDataApi {

    @Inject MarketDataResource resource;

    @PlatformMutation("Generate a synthetic price tick")
    @RestPath("/tick")
    public Object generateTick() {
        return resource.generateTick();
    }

    @PlatformQuery("Get recent market events")
    @RestPath("/recent")
    public Object recent(@QueryParam("limit") int limit) {
        return resource.recent(limit);
    }

    @PlatformQuery("Get OHLCV bars for an instrument")
    @RestPath("/bars/{instrument}")
    public Object bars(@PathParam String instrument, @QueryParam("limit") int limit) {
        return resource.bars(instrument, limit);
    }

    @PlatformQuery("Get trend summaries for an instrument")
    @RestPath("/trends/{instrument}")
    public Object trends(@PathParam String instrument, @QueryParam("limit") int limit) {
        return resource.trends(instrument, limit);
    }

    @PlatformQuery("Get regime assessment for an instrument")
    @RestPath("/regime/{instrument}")
    public Object regime(@PathParam String instrument) {
        return resource.regime(instrument);
    }

    @PlatformQuery("Get session narrative")
    @RestPath("/narrative")
    public Object narrative() {
        return resource.narrative();
    }

    @PlatformMutation("Run a market scenario")
    @RestPath("/scenario")
    public Object scenario(MarketDataResource.ScenarioRequest request) {
        return resource.scenario(request);
    }

    @PlatformMutation("Pause the market data scheduler")
    @RestPath("/scheduler/pause")
    public Object pauseScheduler() {
        resource.pauseScheduler();
        return java.util.Map.of("paused", true);
    }

    @PlatformMutation("Resume the market data scheduler")
    @RestPath("/scheduler/resume")
    public Object resumeScheduler() {
        resource.resumeScheduler();
        return java.util.Map.of("resumed", true);
    }
}
