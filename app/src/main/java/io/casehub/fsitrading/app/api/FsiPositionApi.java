package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.resource.PositionResource;
import io.casehub.fsitrading.app.view.PositionView;
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
    @Inject
            io.casehub.fsitrading.spi.OrderExecutionService orderExecutionService;


    @PlatformQuery("List all positions")
    @RestPath("/")
    public List<PositionView> listAll() {
        return resource.listAll().stream().map(PositionView::toView).toList();
    }

    @PlatformQuery("List positions by strategy")
    @RestPath("/strategy/{strategyId}")
    public List<PositionView> listByStrategy(@PathParam UUID strategyId) {
        return resource.listByStrategy(strategyId).stream().map(PositionView::toView).toList();
    }

    @PlatformMutation("Close a position")
    @RestPath("/close")
    public OrderResult close(CloseRequest request) {
        return orderExecutionService.submit(request.instrument(), OrderSide.SELL, request.quantity(), OrderType.MARKET);
    }

    public record CloseRequest(String instrument, java.math.BigDecimal quantity) {}

}
