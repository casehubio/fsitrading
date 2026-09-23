package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.PositionService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "fsi/positions", basePath = "/api/fsi/positions")
@ApplicationScoped
public class FsiPositionApi {

    @Inject PositionService positionService;

    @PlatformQuery("List all positions")
    @RestPath("/")
    public List<PositionEntity> listAll() {
        return positionService.findAll();
    }

    @PlatformQuery("List positions by strategy")
    @RestPath("/strategy/{strategyId}")
    public List<PositionEntity> listByStrategy(@PathParam UUID strategyId) {
        return positionService.findByStrategy(strategyId);
    }
}
