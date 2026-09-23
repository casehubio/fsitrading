package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.app.model.ArenaRunEntity;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.ledger.runtime.service.federation.TrustExportService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.List;

@McpDomain(value = "fsi/trust", basePath = "/api/fsi/trust")
@ApplicationScoped
public class FsiTrustApi {

    static final int BOOTSTRAP_THRESHOLD = 10;

    @Inject TrustExportService trustExportService;
    @Inject EntityManager em;

    @ConfigProperty(name = "casehub.fsitrading.arena.routing.threshold", defaultValue = "0.3")
    double routingThreshold;

    @ConfigProperty(name = "casehub.fsitrading.arena.approval.timeout-hours", defaultValue = "4")
    int approvalTimeoutHours;

    @PlatformQuery("List trust scores for all strategies")
    @RestPath("/strategies")
    public List<StrategyTrustView> listTrustScores() {
        return Arrays.stream(StrategyType.values())
                .map(this::buildView)
                .toList();
    }

    @PlatformQuery("Get trust score by strategy type")
    @RestPath("/strategies/{strategyType}")
    public StrategyTrustView getTrustByType(@PathParam String strategyType) {
        try {
            var type = StrategyType.valueOf(strategyType.toUpperCase());
            return buildView(type);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown strategy type: " + strategyType);
        }
    }

    @PlatformQuery("List routing decisions")
    @RestPath("/routing/decisions")
    public List<ArenaRunEntity> listRoutingDecisions(@QueryParam("limit") Integer limit) {
        int maxResults = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        return em.createQuery(
                        "SELECT r FROM ArenaRunEntity r ORDER BY r.createdAt DESC",
                        ArenaRunEntity.class)
                .setMaxResults(maxResults)
                .getResultList();
    }

    @PlatformQuery("Get latest routing decision")
    @RestPath("/routing/decisions/latest")
    public ArenaRunEntity latestRoutingDecision() {
        return em.createQuery(
                        "SELECT r FROM ArenaRunEntity r WHERE r.status = 'COMPLETED' ORDER BY r.createdAt DESC",
                        ArenaRunEntity.class)
                .setMaxResults(1)
                .getResultStream().findFirst().orElse(null);
    }

    @PlatformQuery("Get trust routing preferences")
    @RestPath("/preferences")
    public TrustPreferences getPreferences() {
        return new TrustPreferences(routingThreshold, approvalTimeoutHours);
    }

    @PlatformMutation("Update trust routing preferences")
    @RestPath("/preferences")
    public TrustPreferences updatePreferences(TrustPreferencesUpdate updates) {
        return new TrustPreferences(routingThreshold, approvalTimeoutHours,
                "Runtime updates not yet supported — configure via application.properties");
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
                new AttestationBreakdown(positive, negative));
    }

    public record StrategyTrustView(
            String strategyType, String actorId, Double trustScore,
            int decisionCount, String phase, AttestationBreakdown attestations) {}

    public record AttestationBreakdown(int positive, int negative) {}

    public record TrustPreferences(double routingThreshold, int approvalTimeoutHours, String note) {
        public TrustPreferences(double routingThreshold, int approvalTimeoutHours) {
            this(routingThreshold, approvalTimeoutHours, null);
        }
    }

    public record TrustPreferencesUpdate(Double routingThreshold, Integer approvalTimeoutHours) {}
}
