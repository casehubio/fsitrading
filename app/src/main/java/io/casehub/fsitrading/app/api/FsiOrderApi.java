package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.app.service.OrderService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "fsi/orders", basePath = "/api/fsi/orders")
@ApplicationScoped
public class FsiOrderApi {

    @Inject OrderService orderService;

    @PlatformQuery("List all orders")
    @RestPath("/")
    public List<OrderEntity> listAll() {
        return orderService.findAll();
    }

    @PlatformQuery("List orders by strategy")
    @RestPath("/strategy/{strategyId}")
    public List<OrderEntity> listByStrategy(@PathParam UUID strategyId) {
        return orderService.findByStrategy(strategyId);
    }
}
