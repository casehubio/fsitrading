package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.OrderResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@McpDomain(value = "fsi/orders", basePath = "/api/fsi/orders")
@ApplicationScoped
public class FsiOrderApi {

    @Inject OrderResource resource;

    @PlatformQuery("List all orders")
    @RestPath("/")
    public Object listAll() {
        return resource.listAll();
    }

    @PlatformQuery("List orders by strategy")
    @RestPath("/strategy/{strategyId}")
    public Object listByStrategy(@PathParam UUID strategyId) {
        return resource.listByStrategy(strategyId);
    }
}
