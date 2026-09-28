package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.PositionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

@Path("/api/positions")
@Produces(MediaType.APPLICATION_JSON)
public class PositionResource {

    @Inject
    PositionService positionService;

    @GET
    public List<PositionEntity> listAll() {
        return positionService.findAll();
    }

    @GET
    @Path("/strategy/{strategyId}")
    public List<PositionEntity> listByStrategy(@PathParam("strategyId") UUID strategyId) {
        return positionService.findByStrategy(strategyId);
    }
}
