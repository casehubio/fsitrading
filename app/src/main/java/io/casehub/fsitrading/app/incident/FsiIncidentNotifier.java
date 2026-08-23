package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.app.pipeline.FsiMarketPushService;
import io.casehub.fsitrading.app.push.IncidentPushPayload;
import io.casehub.fsitrading.model.GateOpenedEvent;
import io.casehub.fsitrading.model.IncidentCreatedEvent;
import io.casehub.fsitrading.model.IncidentResolvedEvent;
import io.casehub.fsitrading.model.SlaBreachEvent;
import io.casehub.pages.push.EventBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FsiIncidentNotifier {

    private static final Logger log = Logger.getLogger(FsiIncidentNotifier.class);

    private final FsiMarketPushService.PushBroadcaster broadcaster;

    @Inject
    public FsiIncidentNotifier(EventBroadcaster eventBroadcaster) {
        this.broadcaster = eventBroadcaster::broadcast;
    }

    FsiIncidentNotifier(FsiMarketPushService.PushBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    void onIncidentCreated(@Observes IncidentCreatedEvent event) {
        var payload = new IncidentPushPayload.IncidentCreated(
                event.caseId(), event.severity().name(), event.eventType().name(),
                event.instruments(), event.description(),
                event.claimDeadline(), event.completionDeadline(),
                event.createdAt());
        broadcaster.broadcast("incidents/" + event.caseId(), payload);
        broadcaster.broadcast("incidents/summary", payload);
        log.infof("Incident notification: %s %s caseId=%s",
                  event.severity(), event.eventType(), event.caseId());
    }

    void onGateOpened(@Observes GateOpenedEvent event) {
        log.infof("Gate opened notification: caseId=%s reason=%s",
                  event.caseId(), event.actionDescription());
    }

    void onSlaBreach(@Observes SlaBreachEvent event) {
        var payload = new IncidentPushPayload.SlaBreached(
                event.caseId(), event.taskId(), event.breachType(),
                event.tier(), event.severity().name());
        broadcaster.broadcast("incidents/" + event.caseId(), payload);
        log.infof("SLA breach notification: caseId=%s type=%s tier=%d",
                  event.caseId(), event.breachType(), event.tier());
    }

    void onIncidentResolved(@Observes IncidentResolvedEvent event) {
        var payload = new IncidentPushPayload.IncidentResolved(
                event.caseId(), event.resolvedAt());
        broadcaster.broadcast("incidents/" + event.caseId(), payload);
        broadcaster.broadcast("incidents/summary", payload);
        log.infof("Incident resolved notification: caseId=%s", event.caseId());
    }
}
