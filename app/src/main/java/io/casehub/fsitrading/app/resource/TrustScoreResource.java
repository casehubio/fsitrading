package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.ledger.runtime.service.federation.TrustExportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Arrays;
import java.util.List;

@Path("/api/trust/strategies")
@Produces(MediaType.APPLICATION_JSON)
public class TrustScoreResource {

    static final int BOOTSTRAP_THRESHOLD = 10;

    @Inject
    TrustExportService trustExportService;

    @GET
    public List<StrategyTrustView> listAll() {
        return Arrays.stream(StrategyType.values())
                .map(this::buildView)
                .toList();
    }

    @GET
    @Path("/{strategyType}")
    public Response getByType(@PathParam("strategyType") String strategyTypeStr) {
        try {
            var strategyType = StrategyType.valueOf(strategyTypeStr.toUpperCase());
            return Response.ok(buildView(strategyType)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    private StrategyTrustView buildView(StrategyType type) {
        var actorId = FsiActorIdentity.forStrategy(type);
        var capabilityTag = FsiActorIdentity.capabilityTag(type);
        var exportOpt = trustExportService.exportActor(actorId);

        Double trustScore = null;
        int decisionCount = 0;
        int positive = 0;
        int negative = 0;

        if (exportOpt.isPresent()) {
            var payload = exportOpt.get();
            var actors = payload.actors();
            if (!actors.isEmpty()) {
                var actor = actors.get(0);
                var capScore = actor.capabilityScores().stream()
                        .filter(c -> capabilityTag.equals(c.capabilityTag()))
                        .findFirst();
                if (capScore.isPresent()) {
                    trustScore = capScore.get().trustScore();
                    decisionCount = capScore.get().decisionCount();
                    positive = capScore.get().attestationPositive();
                    negative = capScore.get().attestationNegative();
                } else if (actor.globalScore() != null) {
                    trustScore = actor.globalScore().trustScore();
                    decisionCount = actor.globalScore().decisionCount();
                    positive = actor.globalScore().attestationPositive();
                    negative = actor.globalScore().attestationNegative();
                }
            }
        }

        var phase = decisionCount >= BOOTSTRAP_THRESHOLD ? "ACTIVE" : "BOOTSTRAP";

        return new StrategyTrustView(
                type.name(), actorId, trustScore, decisionCount, phase,
                new AttestationSummary(positive, negative));
    }

    public record StrategyTrustView(
            String strategyType, String actorId, Double trustScore,
            int decisionCount, String phase, AttestationSummary attestationSummary) {}

    public record AttestationSummary(int positive, int negative) {}
}
