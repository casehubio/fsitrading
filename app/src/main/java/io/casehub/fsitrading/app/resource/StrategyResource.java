package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.StrategyEntity;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.StrategyType;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/strategies")
@Produces(MediaType.APPLICATION_JSON)
public class StrategyResource {

    @Inject
    StrategyService strategyService;

    @GET
    public List<StrategyEntity> listAll() {
        return strategyService.findAll();
    }

    @GET
    @Path("/active")
    public List<StrategyEntity> listActive() {
        return strategyService.findActive();
    }

    @POST
    public StrategyEntity create(CreateStrategyRequest request) {
        return strategyService.create(request.name(), request.strategyType());
    }

    public record CreateStrategyRequest(String name, StrategyType strategyType) {}
}
