package io.casehub.fsitrading.app.incident;

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

    private final EventBroadcaster broadcaster;

    @Inject
    public FsiIncidentNotifier(EventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    void onIncidentCreated(@Observes IncidentCreatedEvent event) {
        broadcaster.broadcast("incidents/" + event.caseId(), event);
        broadcaster.broadcast("incidents/summary", event);
        log.infof("Incident notification: %s %s caseId=%s",
                event.severity(), event.eventType(), event.caseId());
    }

    void onGateOpened(@Observes GateOpenedEvent event) {
        broadcaster.broadcast("work-items/" + event.caseId(), event);
        log.infof("Gate opened notification: caseId=%s reason=%s",
                event.caseId(), event.actionDescription());
    }

    void onSlaBreach(@Observes SlaBreachEvent event) {
        broadcaster.broadcast("incidents/" + event.caseId(), event);
        log.infof("SLA breach notification: caseId=%s type=%s tier=%d",
                event.caseId(), event.breachType(), event.tier());
    }

    void onIncidentResolved(@Observes IncidentResolvedEvent event) {
        broadcaster.broadcast("incidents/" + event.caseId(), event);
        broadcaster.broadcast("incidents/summary", event);
        log.infof("Incident resolved notification: caseId=%s", event.caseId());
    }
}
