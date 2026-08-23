package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.model.ExternalIncidentRequest;
import io.casehub.fsitrading.model.IncidentCreatedEvent;
import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.IncidentSeverityDescriptor;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeChanged;
import io.casehub.fsitrading.model.TrendReversalDetected;
import io.casehub.fsitrading.spi.IncidentStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class FsiIncidentTrigger {

    private static final Logger log = Logger.getLogger(FsiIncidentTrigger.class);

    private final OvernightIncidentCaseHub caseHub;
    private final IncidentStore store;
    private final Event<IncidentCreatedEvent> incidentCreatedEvent;

    @Inject
    public FsiIncidentTrigger(OvernightIncidentCaseHub caseHub,
                              IncidentStore store,
                              Event<IncidentCreatedEvent> incidentCreatedEvent) {
        this.caseHub = caseHub;
        this.store = store;
        this.incidentCreatedEvent = incidentCreatedEvent;
    }

    @Transactional
    void onTrendReversal(@Observes TrendReversalDetected event) {
        final MarketEventType eventType = inferEventType(event);
        final int hour = LocalTime.now(ZoneId.of("UTC")).getHour();
        final IncidentSeverity severity = classifySeverity(eventType, hour);
        triggerIncident(severity, eventType, List.of(event.instrument()),
                "Trend reversal: " + event.oldDirection() + " → " + event.newDirection());
    }

    @Transactional
    void onRegimeChanged(@Observes RegimeChanged event) {
        final MarketEventType eventType = inferEventType(event);
        final int hour = LocalTime.now(ZoneId.of("UTC")).getHour();
        final IncidentSeverity severity = classifySeverity(eventType, hour);
        triggerIncident(severity, eventType, List.of(event.instrument()),
                "Regime change: " + event.oldRegime() + " → " + event.newRegime());
    }

    @Transactional
    public UUID triggerFromExternal(ExternalIncidentRequest request) {
        return triggerIncident(request.severity(), request.eventType(),
                List.of(request.instrument()), request.description());
    }

    @Transactional
    public UUID triggerSimulated(IncidentSeverity severity, MarketEventType eventType,
                                List<String> instruments, String description) {
        return triggerIncident(severity, eventType, instruments, description);
    }

    private UUID triggerIncident(IncidentSeverity severity, MarketEventType eventType,
                                List<String> instruments, String description) {
        final UUID caseId = caseHub.startCase(Map.of(
                "severity", severity.name(),
                "eventType", eventType.name(),
                "instruments", instruments,
                "description", description));

        var now                = Instant.now();
        var descriptor         = IncidentSeverityDescriptor.forSeverity(severity);
        var claimDeadline      = now.plus(descriptor.claimDeadline());
        var completionDeadline = now.plus(descriptor.completionDeadline());

        final var record = new IncidentRecord(caseId, severity, eventType,
                                              instruments, "DETECTED", now, null, claimDeadline, completionDeadline);
        store.save(record);

        incidentCreatedEvent.fire(new IncidentCreatedEvent(
                caseId, severity, eventType, instruments, description,
                now, claimDeadline, completionDeadline));

        log.infof("Incident triggered: %s %s %s caseId=%s", severity, eventType, instruments, caseId);
        return caseId;
    }

    static IncidentSeverity classifySeverity(MarketEventType eventType, int hour) {
        final IncidentSeverity base = switch (eventType) {
            case FLASH_CRASH, COUNTERPARTY_FAILURE -> IncidentSeverity.CRITICAL;
            case LIQUIDITY_DROP, GAP_OPEN, MARGIN_CALL -> IncidentSeverity.HIGH;
            case CIRCUIT_BREAKER, NEWS_EVENT -> IncidentSeverity.MEDIUM;
            default -> IncidentSeverity.MEDIUM;
        };
        if (base == IncidentSeverity.MEDIUM && (hour < 7 || hour >= 20)) {
            return IncidentSeverity.HIGH;
        }
        return base;
    }

    static MarketEventType inferEventType(TrendReversalDetected reversal) {
        return MarketEventType.FLASH_CRASH;
    }

    static MarketEventType inferEventType(RegimeChanged change) {
        if (change.newRegime() == MarketRegime.VOLATILE) {
            return MarketEventType.NEWS_EVENT;
        }
        return MarketEventType.LIQUIDITY_DROP;
    }
}
