package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.StrategyResource;
import io.casehub.fsitrading.app.view.DeployStrategyRequest;
import io.casehub.fsitrading.app.view.StrategyView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@McpDomain(value = "fsi/strategies", basePath = "/api/fsi/strategies")
@ApplicationScoped
public class FsiStrategyApi {

    @Inject StrategyResource resource;

    @PlatformQuery("List all strategies")
    @RestPath("/")
    public List<StrategyView> listAll() {
        return resource.listAll().stream().map(StrategyView::toView).toList();
    }

    @PlatformQuery("List active strategies")
    @RestPath("/active")
    public List<StrategyView> listActive() {
        return resource.listActive().stream().map(StrategyView::toView).toList();
    }

    @PlatformMutation("Deploy a strategy")
    @RestPath("/")
    public StrategyView deploy(DeployStrategyRequest request) {
        return StrategyView.toView(resource.create(
                new StrategyResource.CreateStrategyRequest(request.name(), request.strategyType())));
    }
}
