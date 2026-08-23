package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.app.push.IncidentPushPayload;
import io.casehub.fsitrading.model.GateOpenedEvent;
import io.casehub.fsitrading.model.IncidentCreatedEvent;
import io.casehub.fsitrading.model.IncidentResolvedEvent;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.model.SlaBreachEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiIncidentNotifierTest {

    private final List<BroadcastCapture> broadcasts = new ArrayList<>();
    private FsiIncidentNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new FsiIncidentNotifier(
                (topic, event) -> broadcasts.add(new BroadcastCapture(topic, event)));
    }

    @Test
    void incidentCreated_broadcastsTypedPayload() {
        var caseId = UUID.randomUUID();
        var now = Instant.now();
        var event = new IncidentCreatedEvent(
                caseId, IncidentSeverity.CRITICAL, MarketEventType.FLASH_CRASH,
                List.of("AAPL"), "Flash crash", now,
                now.plusSeconds(120), now.plusSeconds(300));

        notifier.onIncidentCreated(event);

        assertEquals(2, broadcasts.size());
        assertEquals("incident:" + caseId, broadcasts.get(0).topic);
        assertEquals("incident:summary", broadcasts.get(1).topic);
        var payload = (IncidentPushPayload.IncidentCreated) broadcasts.get(0).event;
        assertEquals("INCIDENT_CREATED", payload.type());
        assertEquals(caseId, payload.caseId());
        assertEquals("CRITICAL", payload.severity());
        assertEquals(now, payload.createdAt());
        assertEquals(now.plusSeconds(120), payload.claimDeadline());
    }

    @Test
    void slaBreached_broadcastsTypedPayload() {
        var caseId = UUID.randomUUID();
        var taskId = UUID.randomUUID();
        var event = new SlaBreachEvent(caseId, taskId, "CLAIM_EXPIRED", 1, IncidentSeverity.CRITICAL);

        notifier.onSlaBreach(event);

        assertEquals(1, broadcasts.size());
        assertEquals("incident:" + caseId, broadcasts.get(0).topic);
        var payload = (IncidentPushPayload.SlaBreached) broadcasts.get(0).event;
        assertEquals("SLA_BREACHED", payload.type());
        assertEquals(1, payload.tier());
    }

    @Test
    void incidentResolved_broadcastsTypedPayload() {
        var caseId = UUID.randomUUID();
        var now = Instant.now();
        var event = new IncidentResolvedEvent(caseId, IncidentSeverity.HIGH, "Resolved", now);

        notifier.onIncidentResolved(event);

        assertEquals(2, broadcasts.size());
        assertEquals("incident:" + caseId, broadcasts.get(0).topic);
        assertEquals("incident:summary", broadcasts.get(1).topic);
        var payload = (IncidentPushPayload.IncidentResolved) broadcasts.get(0).event;
        assertEquals("INCIDENT_RESOLVED", payload.type());
        assertEquals(now, payload.resolvedAt());
    }

    @Test
    void gateOpened_doesNotBroadcast() {
        var event = new GateOpenedEvent(
                UUID.randomUUID(), "Close 30% portfolio", "HIGH", "fsi-oncall");
        notifier.onGateOpened(event);
        assertTrue(broadcasts.isEmpty());
    }

    record BroadcastCapture(String topic, Object event) {}
}
