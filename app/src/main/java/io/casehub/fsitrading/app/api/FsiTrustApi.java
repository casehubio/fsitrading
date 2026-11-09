package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.PreferencesResource;
import io.casehub.fsitrading.app.resource.RoutingDecisionResource;
import io.casehub.fsitrading.app.resource.TrustScoreResource;
import io.casehub.fsitrading.app.resource.TrustScoreResource.StrategyTrustView;
import io.casehub.fsitrading.app.view.RoutingDecisionView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@McpDomain(value = "fsi/trust", basePath = "/api/fsi/trust")
@ApplicationScoped
public class FsiTrustApi {

    @Inject TrustScoreResource trustResource;
    @Inject RoutingDecisionResource routingResource;
    @Inject PreferencesResource preferencesResource;

    @PlatformQuery("List trust scores for all strategies")
    @RestPath("/scores")
    public List<StrategyTrustView> listTrustScores() {
        return trustResource.listAll();
    }

    @PlatformQuery("Get trust score by strategy type")
    @RestPath("/scores/{strategyType}")
    public StrategyTrustView getTrustByType(String strategyType) {
        return (StrategyTrustView) trustResource.getByType(strategyType).getEntity();
    }

    @PlatformQuery("List routing decisions")
    @RestPath("/routing-decisions")
    public List<RoutingDecisionView> listRoutingDecisions(Integer limit) {
        return routingResource.listDecisions(limit).stream().map(RoutingDecisionView::toView).toList();
    }

    @PlatformQuery("Get latest routing decision")
    @RestPath("/routing-decisions/latest")
    public RoutingDecisionView latestRoutingDecision() {
        var entity = routingResource.latestDecision();
        return entity != null ? RoutingDecisionView.toView(entity) : null;
    }

    @PlatformQuery("Get trust routing preferences")
    @RestPath("/preferences")
    public Map<String, Object> getPreferences() {
        return preferencesResource.getPreferences();
    }

}
