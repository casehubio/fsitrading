package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.model.GateOpenedEvent;
import io.casehub.fsitrading.model.IncidentCreatedEvent;
import io.casehub.fsitrading.model.IncidentResolvedEvent;
import io.casehub.fsitrading.model.SlaBreachEvent;
import io.casehub.platform.api.subscription.EventFieldDescriptor;
import io.casehub.platform.api.subscription.EventTypeDescriptor;
import io.casehub.platform.api.subscription.EventTypeRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class FsiEventTypeRegistrar {

    private static final Logger log = Logger.getLogger(FsiEventTypeRegistrar.class);

    private final EventTypeRegistry registry;

    @Inject
    public FsiEventTypeRegistrar(EventTypeRegistry registry) {
        this.registry = registry;
    }

    void onStart(@Observes StartupEvent event) {
        registry.register(new EventTypeDescriptor(
                IncidentCreatedEvent.class.getSimpleName(),
                "Incident Created",
                "Fired when a new overnight incident case is created",
                List.of(
                        new EventFieldDescriptor("caseId", "Case ID", "UUID"),
                        new EventFieldDescriptor("severity", "Severity", "IncidentSeverity"),
                        new EventFieldDescriptor("eventType", "Event Type", "MarketEventType"),
                        new EventFieldDescriptor("instruments", "Instruments", "List<String>"),
                        new EventFieldDescriptor("createdAt", "Created At", "Instant"),
                        new EventFieldDescriptor("claimDeadline", "Claim Deadline", "Instant"),
                        new EventFieldDescriptor("completionDeadline", "Completion Deadline", "Instant"))));

        registry.register(new EventTypeDescriptor(
                GateOpenedEvent.class.getSimpleName(),
                "Gate Opened",
                "Fired when a high-risk action requires human approval",
                List.of(
                        new EventFieldDescriptor("caseId", "Case ID", "UUID"),
                        new EventFieldDescriptor("actionDescription", "Action", "String"),
                        new EventFieldDescriptor("riskLevel", "Risk Level", "String"))));

        registry.register(new EventTypeDescriptor(
                SlaBreachEvent.class.getSimpleName(),
                "SLA Breach",
                "Fired when an SLA deadline is breached",
                List.of(
                        new EventFieldDescriptor("caseId", "Case ID", "UUID"),
                        new EventFieldDescriptor("breachType", "Breach Type", "String"),
                        new EventFieldDescriptor("tier", "Tier", "int"),
                        new EventFieldDescriptor("severity", "Severity", "IncidentSeverity"))));

        registry.register(new EventTypeDescriptor(
                IncidentResolvedEvent.class.getSimpleName(),
                "Incident Resolved",
                "Fired when an incident case reaches CLOSED milestone",
                List.of(
                        new EventFieldDescriptor("caseId", "Case ID", "UUID"),
                        new EventFieldDescriptor("severity", "Severity", "IncidentSeverity"),
                        new EventFieldDescriptor("resolution", "Resolution", "String"),
                        new EventFieldDescriptor("resolvedAt", "Resolved At", "Instant"))));

        log.info("Registered 4 FSI incident event types with EventTypeRegistry");
    }
}
