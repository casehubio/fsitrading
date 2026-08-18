package io.casehub.fsitrading.app.push;

import io.casehub.fsitrading.app.arena.RoutingDecisionEvent;
import io.casehub.fsitrading.app.arena.TrustScoreChangedEvent;
import io.casehub.fsitrading.app.pipeline.FsiMarketPushService;
import io.casehub.fsitrading.app.service.PositionUpdatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class FsiTradingPushListener {

    private final FsiMarketPushService.PushBroadcaster broadcaster;

    @Inject
    public FsiTradingPushListener(io.casehub.pages.push.EventBroadcaster eventBroadcaster) {
        this.broadcaster = eventBroadcaster::broadcast;
    }

    FsiTradingPushListener(FsiMarketPushService.PushBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    void onPositionUpdated(@Observes PositionUpdatedEvent event) {
        var posPayload = new TradingPushPayload.PositionUpdate(
                event.positionId(), event.instrument(), event.assetClass(),
                event.strategyId(), event.quantity(), event.avgCost(),
                event.realizedPnl(), event.updatedAt());
        broadcaster.broadcast("position:" + event.instrument(), posPayload);

        if (event.realizedPnl() != null && event.closedQuantity() != null) {
            var pnlPayload = new TradingPushPayload.PnlUpdate(
                    event.strategyId(), event.instrument(), event.realizedPnl(),
                    event.fillPrice(), event.closedQuantity(), event.updatedAt());
            broadcaster.broadcast("pnl:" + event.strategyId(), pnlPayload);
        }
    }

    void onTrustChanged(@Observes TrustScoreChangedEvent event) {
        var payload = new TradingPushPayload.TrustUpdate(
                event.strategyType(), event.actorId(), event.trustScore(),
                event.decisionCount(), event.phase());
        broadcaster.broadcast("trust:" + event.strategyType(), payload);
    }

    void onRoutingDecision(@Observes RoutingDecisionEvent event) {
        var payload = new TradingPushPayload.RoutingUpdate(
                event.evaluationId(), event.instrument(), event.selectedAgents(),
                event.routingStrategy(), event.decidedAt());
        broadcaster.broadcast("routing:latest", payload);
    }
}
