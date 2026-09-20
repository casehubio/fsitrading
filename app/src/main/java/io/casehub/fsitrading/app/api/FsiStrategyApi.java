package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.StrategyResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpDomain(value = "fsi/strategies", basePath = "/api/fsi/strategies")
@ApplicationScoped
public class FsiStrategyApi {

    @Inject StrategyResource resource;

    @PlatformQuery("List all strategies")
    @RestPath("/")
    public Object listAll() {
        return resource.listAll();
    }

    @PlatformQuery("List active strategies")
    @RestPath("/active")
    public Object listActive() {
        return resource.listActive();
    }

    @PlatformMutation("Deploy a strategy")
    @RestPath("/deploy")
    public Object deploy(java.util.Map<String, Object> body) {
        return resource.deploy(body).getEntity();
    }
}
