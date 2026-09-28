package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.app.service.OrderService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService orderService;

    @GET
    public List<OrderEntity> listAll() {
        return orderService.findAll();
    }

    @GET
    @Path("/strategy/{strategyId}")
    public List<OrderEntity> listByStrategy(@PathParam("strategyId") UUID strategyId) {
        return orderService.findByStrategy(strategyId);
    }
}
