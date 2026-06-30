package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.app.service.SyntheticMarketDataProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/market-data")
@Produces(MediaType.APPLICATION_JSON)
public class MarketDataResource {

    @Inject
    SyntheticMarketDataProvider marketDataProvider;

    @POST
    @Path("/tick")
    public MarketEventEntity generateTick() {
        return marketDataProvider.generateTick();
    }

    @GET
    @Path("/recent")
    public List<MarketEventEntity> recent(@QueryParam("limit") @DefaultValue("20") int limit) {
        return marketDataProvider.findRecent(limit);
    }
}
