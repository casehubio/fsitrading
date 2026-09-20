package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.PositionResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@McpDomain(value = "fsi/positions", basePath = "/api/fsi/positions")
@ApplicationScoped
public class FsiPositionApi {

    @Inject PositionResource resource;

    @PlatformQuery("List all positions")
    @RestPath("/")
    public Object listAll() {
        return resource.listAll();
    }

    @PlatformQuery("List positions by strategy")
    @RestPath("/strategy/{strategyId}")
    public Object listByStrategy(@PathParam UUID strategyId) {
        return resource.listByStrategy(strategyId);
    }
}
