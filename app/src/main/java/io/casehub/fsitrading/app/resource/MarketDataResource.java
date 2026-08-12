package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.app.model.OhlcvBarEntity;
import io.casehub.fsitrading.app.model.TrendSummaryEntity;
import io.casehub.fsitrading.app.pipeline.FsiObservationCache;
import io.casehub.fsitrading.app.pipeline.MarketPulseScheduler;
import io.casehub.fsitrading.app.service.ScenarioRunner;
import io.casehub.fsitrading.app.service.SyntheticMarketDataProvider;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.ScenarioType;
import io.casehub.fsitrading.model.SessionNarrative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/market-data")
@Produces(MediaType.APPLICATION_JSON)
public class MarketDataResource {

    @Inject SyntheticMarketDataProvider marketDataProvider;
    @Inject ScenarioRunner scenarioRunner;
    @Inject FsiObservationCache observationCache;
    @Inject MarketPulseScheduler scheduler;
    @Inject EntityManager em;

    @POST @Path("/tick")
    public PriceTick generateTick() {
        return marketDataProvider.generateTick();
    }

    @GET @Path("/recent")
    public List<MarketEventEntity> recent(@QueryParam("limit") @DefaultValue("20") int limit) {
        return marketDataProvider.findRecent(limit);
    }

    @GET @Path("/bars/{instrument}")
    public List<OhlcvBarEntity> bars(@PathParam("instrument") String instrument,
                                     @QueryParam("limit") @DefaultValue("60") int limit) {
        return em.createQuery(
                        "SELECT b FROM OhlcvBarEntity b WHERE b.instrument = :instrument ORDER BY b.windowStart DESC",
                        OhlcvBarEntity.class)
                .setParameter("instrument", instrument)
                .setMaxResults(limit)
                .getResultList();
    }

    @GET @Path("/trends/{instrument}")
    public List<TrendSummaryEntity> trends(@PathParam("instrument") String instrument,
                                           @QueryParam("limit") @DefaultValue("20") int limit) {
        return em.createQuery(
                        "SELECT t FROM TrendSummaryEntity t WHERE t.instrument = :instrument ORDER BY t.windowStart DESC",
                        TrendSummaryEntity.class)
                .setParameter("instrument", instrument)
                .setMaxResults(limit)
                .getResultList();
    }

    @GET @Path("/regime/{instrument}")
    public RegimeAssessment regime(@PathParam("instrument") String instrument) {
        return observationCache.latestRegime(instrument).orElse(null);
    }

    @GET @Path("/narrative")
    public SessionNarrative narrative() {
        return observationCache.latestNarrative().orElse(null);
    }

    @POST @Path("/scenario")
    public Map<String, Object> scenario(ScenarioRequest request) {
        List<PriceTick> ticks = scenarioRunner.generate(request.scenarioType());
        return Map.of("scenarioType", request.scenarioType(), "tickCount", ticks.size());
    }

    @POST @Path("/scheduler/pause")
    public Response pauseScheduler() {
        scheduler.pause();
        return Response.noContent().build();
    }

    @POST @Path("/scheduler/resume")
    public Response resumeScheduler() {
        scheduler.resume();
        return Response.noContent().build();
    }

    public record ScenarioRequest(ScenarioType scenarioType) {}
}
