package io.casehub.fsitrading.app.deliberation;

import io.casehub.fsitrading.app.pipeline.FsiMarketPushService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class FsiDeliberationPushListener {

    private final FsiMarketPushService.PushBroadcaster broadcaster;

    @Inject
    public FsiDeliberationPushListener(io.casehub.pages.push.EventBroadcaster eventBroadcaster) {
        this.broadcaster = eventBroadcaster::broadcast;
    }

    FsiDeliberationPushListener(FsiMarketPushService.PushBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    void onStarted(@Observes DeliberationStartedEvent event) {
        var payload = new DeliberationPushPayload.Started(
                event.deliberationId(), event.channelId(), event.instrument(),
                event.triggerType(), event.startedAt());
        broadcaster.broadcast("deliberation:active", payload);
        broadcaster.broadcast("deliberation:" + event.channelId(), payload);
    }

    void onCompleted(@Observes DeliberationCompletedEvent event) {
        var payload = new DeliberationPushPayload.Completed(
                event.deliberationId(), event.channelId(), event.instrument(),
                event.convergenceState(), event.confidence(),
                event.establishedCount(), event.disputedCount(), event.pendingCount(),
                event.rounds(), event.commitmentId(), event.tradeDecisionId(),
                event.outcomeType(), event.endedAt());
        broadcaster.broadcast("deliberation:active", payload);
        broadcaster.broadcast("deliberation:" + event.channelId(), payload);
    }

    void onFailed(@Observes DeliberationFailedEvent event) {
        var payload = new DeliberationPushPayload.Failed(
                event.deliberationId(), event.channelId(), event.instrument(),
                event.reason(), event.endedAt());
        broadcaster.broadcast("deliberation:active", payload);
        broadcaster.broadcast("deliberation:" + event.channelId(), payload);
    }
}
