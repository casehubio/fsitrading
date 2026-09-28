package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.PreferencesResource;
import io.casehub.fsitrading.app.resource.RoutingDecisionResource;
import io.casehub.fsitrading.app.resource.TrustScoreResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.util.Map;

@McpDomain(value = "fsi/trust", basePath = "/api/fsi/trust")
@ApplicationScoped
public class FsiTrustApi {

    @Inject TrustScoreResource trustResource;
    @Inject RoutingDecisionResource routingResource;
    @Inject PreferencesResource preferencesResource;

    @PlatformQuery("List trust scores for all strategies")
    @RestPath("/strategies")
    public Object listTrustScores() {
        return trustResource.listAll();
    }

    @PlatformQuery("Get trust score by strategy type")
    @RestPath("/strategies/{strategyType}")
    public Object getTrustByType(@PathParam String strategyType) {
        return trustResource.getByType(strategyType).getEntity();
    }

    @PlatformQuery("List routing decisions")
    @RestPath("/routing/decisions")
    public Object listRoutingDecisions(@QueryParam("limit") Integer limit) {
        return routingResource.listDecisions(limit);
    }

    @PlatformQuery("Get latest routing decision")
    @RestPath("/routing/decisions/latest")
    public Object latestRoutingDecision() {
        return routingResource.latest().getEntity();
    }

    @PlatformQuery("Get trust routing preferences")
    @RestPath("/preferences")
    public Object getPreferences() {
        return preferencesResource.getPreferences();
    }

    @PlatformMutation("Update trust routing preferences")
    @RestPath("/preferences")
    public Object updatePreferences(Map<String, Object> updates) {
        return preferencesResource.updatePreferences(updates);
    }
}
