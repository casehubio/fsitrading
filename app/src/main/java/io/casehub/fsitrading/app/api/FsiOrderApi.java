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
    @Inject
            io.casehub.fsitrading.app.service.OrderSemaphoreService semaphoreService;


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

    @io.casehub.platform.api.mcp.PlatformMutation("Halt new orders for instrument")
    @io.casehub.platform.api.mcp.RestPath("/halt")
    public Object halt(HaltRequest request) {
        return semaphoreService.halt(request.reason(), request.scope(), request.instrument(), request.playbookInstanceId());
    }

    @io.casehub.platform.api.mcp.PlatformMutation("Release order halt")
    @io.casehub.platform.api.mcp.RestPath("/release")
    public Object release(ReleaseRequest request) {
        return semaphoreService.release(request.ticketId());
    }

    @io.casehub.platform.api.mcp.PlatformMutation("Reconcile orders after incident")
    @io.casehub.platform.api.mcp.RestPath("/reconcile")
    public Object reconcile(ReconcileRequest request) {
        return semaphoreService.releaseAllForPlaybook(request.playbookInstanceId());
    }

    public record HaltRequest(String reason, String scope, String instrument, String playbookInstanceId) {}

    public record ReleaseRequest(java.util.UUID ticketId) {}

    public record ReconcileRequest(String playbookInstanceId) {}

}
