package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.model.StrategyEntity;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.StrategyType;
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

    @Inject StrategyService strategyService;

    @PlatformQuery("List all strategies")
    @RestPath("/")
    public List<StrategyEntity> listAll() {
        return strategyService.findAll();
    }

    @PlatformQuery("List active strategies")
    @RestPath("/active")
    public List<StrategyEntity> listActive() {
        return strategyService.findActive();
    }

    @PlatformMutation("Deploy a strategy")
    @RestPath("/deploy")
    public StrategyEntity deploy(DeployRequest request) {
        return strategyService.create(request.name(), request.strategyType());
    }

    public record DeployRequest(String name, StrategyType strategyType) {}
}
