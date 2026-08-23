package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.model.ExternalIncidentRequest;
import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.IncidentTimelineRecord;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.spi.IncidentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentResourceTest {

    private IncidentResource resource;
    private IncidentStore store;
    private FsiIncidentTrigger trigger;

    @BeforeEach
    void setUp() {
        store = mock(IncidentStore.class);
        trigger = mock(FsiIncidentTrigger.class);
        resource = new IncidentResource();
        resource.store = store;
        resource.trigger = trigger;
    }

    @Test
    void listIncidents_delegatesToStore() {
        var record = new IncidentRecord(UUID.randomUUID(), IncidentSeverity.HIGH,
                MarketEventType.LIQUIDITY_DROP, List.of("AAPL"), "DETECTED", Instant.now(), null, null, null);
        when(store.findRecent(anyInt())).thenReturn(List.of(record));

        var result = resource.list(10);
        assertEquals(1, result.size());
        assertEquals(IncidentSeverity.HIGH, result.get(0).severity());
    }

    @Test
    void getIncident_delegatesToStore() {
        var caseId = UUID.randomUUID();
        var record = new IncidentRecord(caseId, IncidentSeverity.CRITICAL,
                MarketEventType.FLASH_CRASH, List.of("MSFT"), "DETECTED", Instant.now(), null, null, null);
        when(store.findByCaseId(caseId)).thenReturn(record);

        var result = resource.get(caseId);
        assertNotNull(result);
        assertEquals(caseId, result.caseId());
    }

    @Test
    void getIncident_notFound_throws404() {
        var caseId = UUID.randomUUID();
        when(store.findByCaseId(caseId)).thenReturn(null);
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> resource.get(caseId));
    }


    @Test
    void getTimeline_delegatesToStore() {
        var caseId = UUID.randomUUID();
        when(store.getTimeline(caseId)).thenReturn(List.of(
                new IncidentTimelineRecord("DETECTED", Instant.now(), "Initial detection")));

        var result = resource.timeline(caseId);
        assertEquals(1, result.size());
        assertEquals("DETECTED", result.get(0).milestone());
    }

    @Test
    void simulate_callsTrigger() {
        var expectedId = UUID.randomUUID();
        when(trigger.triggerSimulated(
                eq(IncidentSeverity.CRITICAL), eq(MarketEventType.FLASH_CRASH),
                eq(List.of("AAPL")), eq("Test simulation")))
                .thenReturn(expectedId);

        var request = new IncidentResource.SimulateRequest(
                IncidentSeverity.CRITICAL, MarketEventType.FLASH_CRASH,
                List.of("AAPL"), "Test simulation");

        try (var response = resource.simulate(request)) {
            assertEquals(201, response.getStatus());
        }
    }

    @Test
    void external_callsTrigger() {
        var expectedId = UUID.randomUUID();
        var request = new ExternalIncidentRequest(
                MarketEventType.COUNTERPARTY_FAILURE, "AAPL",
                IncidentSeverity.CRITICAL, "Counterparty default", "broker-api");
        when(trigger.triggerFromExternal(request)).thenReturn(expectedId);

        try (var response = resource.external(request)) {
            assertEquals(201, response.getStatus());
        }

        verify(trigger).triggerFromExternal(request);
    }
}
