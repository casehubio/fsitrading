package io.casehub.fsitrading.app.incident.store;

import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.IncidentTimelineRecord;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.spi.IncidentStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JpaIncidentStoreTest {

    @Inject
    IncidentStore store;

    @Test
    @Transactional
    void saveAndFindByCaseId() {
        var caseId = UUID.randomUUID();
        var record = new IncidentRecord(caseId, IncidentSeverity.CRITICAL,
                MarketEventType.FLASH_CRASH, List.of("AAPL", "MSFT"),
                "DETECTED", Instant.now(), null, null, null);
        store.save(record);

        var found = store.findByCaseId(caseId);
        assertNotNull(found);
        assertEquals(IncidentSeverity.CRITICAL, found.severity());
        assertEquals(MarketEventType.FLASH_CRASH, found.eventType());
        assertEquals(List.of("AAPL", "MSFT"), found.instruments());
        assertEquals("DETECTED", found.status());
        assertNull(found.resolvedAt());
    }

    @Test
    @Transactional
    void findRecent_returnsInOrder() {
        for (int i = 0; i < 3; i++) {
            store.save(new IncidentRecord(UUID.randomUUID(), IncidentSeverity.HIGH,
                    MarketEventType.LIQUIDITY_DROP, List.of("TSLA"),
                    "DETECTED", Instant.now(), null, null, null));
        }
        var recent = store.findRecent(2);
        assertEquals(2, recent.size());
    }

    @Test
    @Transactional
    void findByStatus_filtersCorrectly() {
        var id1 = UUID.randomUUID();
        var id2 = UUID.randomUUID();
        store.save(new IncidentRecord(id1, IncidentSeverity.HIGH,
                MarketEventType.LIQUIDITY_DROP, List.of("AAPL"),
                "DETECTED", Instant.now(), null, null, null));
        store.save(new IncidentRecord(id2, IncidentSeverity.MEDIUM,
                MarketEventType.GAP_OPEN, List.of("MSFT"),
                "RESOLVED", Instant.now(), Instant.now(), null, null));

        var detected = store.findByStatus("DETECTED");
        assertTrue(detected.stream().anyMatch(r -> r.caseId().equals(id1)));
        assertTrue(detected.stream().noneMatch(r -> r.caseId().equals(id2)));
    }

    @Test
    @Transactional
    void updateStatus_changesStatus() {
        var caseId = UUID.randomUUID();
        store.save(new IncidentRecord(caseId, IncidentSeverity.CRITICAL,
                MarketEventType.FLASH_CRASH, List.of("AAPL"),
                "DETECTED", Instant.now(), null, null, null));
        store.updateStatus(caseId, "RESPONDED");

        var found = store.findByCaseId(caseId);
        assertEquals("RESPONDED", found.status());
    }

    @Test
    @Transactional
    void addTimelineEntry_andRetrieve() {
        var caseId = UUID.randomUUID();
        store.save(new IncidentRecord(caseId, IncidentSeverity.HIGH,
                MarketEventType.LIQUIDITY_DROP, List.of("MSFT"),
                "DETECTED", Instant.now(), null, null, null));

        store.addTimelineEntry(caseId,
                new IncidentTimelineRecord("CLASSIFIED", Instant.now(), "Severity HIGH"));
        store.addTimelineEntry(caseId,
                new IncidentTimelineRecord("RESPONDED", Instant.now(), "Positions reduced"));

        var timeline = store.getTimeline(caseId);
        assertEquals(2, timeline.size());
        assertEquals("CLASSIFIED", timeline.get(0).milestone());
        assertEquals("RESPONDED", timeline.get(1).milestone());
    }
}
