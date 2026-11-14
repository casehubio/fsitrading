package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.OrderResource;
import io.casehub.fsitrading.app.service.OrderSemaphoreService.SemaphoreTicket;
import io.casehub.fsitrading.app.view.OrderView;
import io.casehub.fsitrading.app.view.ReconcileResultView;
import io.casehub.fsitrading.app.view.ReleaseResultView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "fsi/orders", basePath = "/api/fsi/orders")
@ApplicationScoped
public class FsiOrderApi {

    @Inject OrderResource resource;
    @Inject
            io.casehub.fsitrading.app.service.OrderSemaphoreService semaphoreService;


    @PlatformQuery("List all orders")
    @RestPath("/")
    public List<OrderView> listAll() {
        return resource.listAll().stream().map(OrderView::toView).toList();
    }

    @PlatformQuery("List orders by strategy")
    @RestPath("/strategy/{strategyId}")
    public List<OrderView> listByStrategy(@PathParam UUID strategyId) {
        return resource.listByStrategy(strategyId).stream().map(OrderView::toView).toList();
    }

    @PlatformMutation("Halt order execution")
    @RestPath("/halt")
    public SemaphoreTicket halt(HaltRequest request) {
        return semaphoreService.halt(request.reason(), request.scope(), request.instrument(), request.playbookInstanceId());
    }

    @PlatformMutation("Release an order halt")
    @RestPath("/release")
    public ReleaseResultView release(ReleaseRequest request) {
        boolean released = semaphoreService.release(request.ticketId());
        return new ReleaseResultView(released, request.ticketId());
    }

    @PlatformMutation("Reconcile all halts for a playbook")
    @RestPath("/reconcile")
    public ReconcileResultView reconcile(ReconcileRequest request) {
        int count = semaphoreService.releaseAllForPlaybook(request.playbookInstanceId());
        return new ReconcileResultView(count, request.playbookInstanceId());
    }

    public record HaltRequest(String reason, String scope, String instrument, String playbookInstanceId) {}

    public record ReleaseRequest(java.util.UUID ticketId) {}

    public record ReconcileRequest(String playbookInstanceId) {}

}
