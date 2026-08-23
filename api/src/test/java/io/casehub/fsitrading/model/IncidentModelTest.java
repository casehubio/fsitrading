package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentModelTest {

    @Test
    void marketEventType_includesOperationalEvents() {
        assertNotNull(MarketEventType.valueOf("COUNTERPARTY_FAILURE"));
        assertNotNull(MarketEventType.valueOf("MARGIN_CALL"));
        assertEquals(9, MarketEventType.values().length);
    }

    @Test
    void incidentSeverity_hasThreeLevelsInOrder() {
        assertEquals(3, IncidentSeverity.values().length);
        assertTrue(IncidentSeverity.CRITICAL.compareTo(IncidentSeverity.HIGH) < 0);
        assertTrue(IncidentSeverity.HIGH.compareTo(IncidentSeverity.MEDIUM) < 0);
    }

    @Test
    void incidentCreatedEvent_carriesContext() {
        var caseId             = UUID.randomUUID();
        var now                = Instant.now();
        var claimDeadline      = now.plusSeconds(120);
        var completionDeadline = now.plusSeconds(300);
        var event = new IncidentCreatedEvent(
                caseId, IncidentSeverity.CRITICAL,
                MarketEventType.FLASH_CRASH, List.of("AAPL"), "Flash crash detected",
                now, claimDeadline, completionDeadline);
        assertEquals(caseId, event.caseId());
        assertEquals(IncidentSeverity.CRITICAL, event.severity());
        assertEquals(MarketEventType.FLASH_CRASH, event.eventType());
        assertEquals(List.of("AAPL"), event.instruments());
        assertEquals("Flash crash detected", event.description());
        assertEquals(now, event.createdAt());
        assertEquals(claimDeadline, event.claimDeadline());
        assertEquals(completionDeadline, event.completionDeadline());
    }

    @Test
    void gateOpenedEvent_carriesActionContext() {
        var event = new GateOpenedEvent(
                UUID.randomUUID(), "Close 30% portfolio", "HIGH", "fsi-oncall");
        assertEquals("HIGH", event.riskLevel());
        assertEquals("fsi-oncall", event.candidateGroups());
    }

    @Test
    void slaBreachEvent_carriesTierInfo() {
        var event = new SlaBreachEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                "CLAIM_EXPIRED", 1, IncidentSeverity.CRITICAL);
        assertEquals(1, event.tier());
        assertEquals("CLAIM_EXPIRED", event.breachType());
    }

    @Test
    void incidentResolvedEvent_carriesResolution() {
        var now = Instant.now();
        var event = new IncidentResolvedEvent(
                UUID.randomUUID(), IncidentSeverity.HIGH, "Positions closed successfully", now);
        assertEquals("Positions closed successfully", event.resolution());
        assertEquals(now, event.resolvedAt());
    }

    @Test
    void incidentRecord_domainPojo() {
        var caseId = UUID.randomUUID();
        var now = Instant.now();
        var record = new IncidentRecord(
                caseId, IncidentSeverity.HIGH,
                MarketEventType.LIQUIDITY_DROP, List.of("MSFT"),
                "DETECTED", now, null, null, null);
        assertEquals(caseId, record.caseId());
        assertEquals(IncidentSeverity.HIGH, record.severity());
        assertEquals(List.of("MSFT"), record.instruments());
        assertEquals("DETECTED", record.status());
        assertEquals(now, record.createdAt());
        assertNull(record.resolvedAt());
    }

    @Test
    void incidentTimelineRecord_domainPojo() {
        var now = Instant.now();
        var record = new IncidentTimelineRecord("CLASSIFIED", now, "Severity classified as HIGH");
        assertEquals("CLASSIFIED", record.milestone());
        assertEquals(now, record.timestamp());
        assertEquals("Severity classified as HIGH", record.description());
    }

    @Test
    void externalIncidentRequest_carriesAllFields() {
        var req = new ExternalIncidentRequest(
                MarketEventType.COUNTERPARTY_FAILURE, "AAPL",
                IncidentSeverity.HIGH, "Broker notification", "broker-feed");
        assertEquals(MarketEventType.COUNTERPARTY_FAILURE, req.eventType());
        assertEquals("AAPL", req.instrument());
        assertEquals(IncidentSeverity.HIGH, req.severity());
        assertEquals("Broker notification", req.description());
        assertEquals("broker-feed", req.source());
    }
}
