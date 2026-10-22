package io.casehub.fsitrading.app.deliberation;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DeliberationRecordTest {

    @Inject
    DeliberationRecordRepository repository;

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void persistAndRetrieve() {
        var id = UUID.randomUUID();
        var channelId = UUID.randomUUID();
        var record = new DeliberationRecord(id, channelId, "AAPL",
                "IN_PROGRESS", "REGIME_CHANGED", "rule:momentum@v1,rule:mean-reversion@v1",
                Instant.now());

        repository.persist(record);
        em.flush();

        var found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("AAPL", found.get().getInstrument());
        assertEquals("IN_PROGRESS", found.get().getStatus());
        assertEquals("REGIME_CHANGED", found.get().getTriggerType());
        assertEquals(channelId, found.get().getChannelId());
    }

    @Test
    @Transactional
    void findByInstrumentAndStatus() {
        var record = new DeliberationRecord(UUID.randomUUID(), UUID.randomUUID(), "MSFT",
                "COMPLETED", "TREND_REVERSAL", "rule:momentum@v1",
                Instant.now());
        record.setConvergenceState("CONSENSUS");
        record.setConfidence(0.85);
        record.setEndedAt(Instant.now());
        repository.persist(record);
        em.flush();

        var results = repository.findByInstrumentAndStatus("MSFT", "COMPLETED");
        assertFalse(results.isEmpty());
        assertEquals("CONSENSUS", results.get(0).getConvergenceState());
    }

    @Test
    @Transactional
    void findInProgressReturnsMatch() {
        var record = new DeliberationRecord(UUID.randomUUID(), UUID.randomUUID(), "TSLA",
                "IN_PROGRESS", "MANUAL", "rule:momentum@v1",
                Instant.now());
        repository.persist(record);
        em.flush();

        var found = repository.findInProgress("TSLA");
        assertTrue(found.isPresent());
    }

    @Test
    @Transactional
    void findInProgressReturnsEmptyWhenNone() {
        var found = repository.findInProgress("NONEXISTENT");
        assertTrue(found.isEmpty());
    }

    @Test
    @Transactional
    void findInProgressDetectsExistingDeliberation() {
        var record = new DeliberationRecord(UUID.randomUUID(), UUID.randomUUID(), "GOOG",
                "IN_PROGRESS", "REGIME_CHANGED", "rule:momentum@v1",
                Instant.now());
        repository.persist(record);
        em.flush();

        assertTrue(repository.findInProgress("GOOG").isPresent());
    }

    @Test
    @Transactional
    void multipleCompletedForSameInstrumentAllowed() {
        for (int i = 0; i < 2; i++) {
            var record = new DeliberationRecord(UUID.randomUUID(), UUID.randomUUID(), "AMZN",
                    "COMPLETED", "REGIME_CHANGED", "rule:momentum@v1",
                    Instant.now());
            record.setEndedAt(Instant.now());
            repository.persist(record);
        }
        em.flush();

        var results = repository.findByInstrumentAndStatus("AMZN", "COMPLETED");
        assertEquals(2, results.size());
    }

    @Test
    @Transactional
    void updateMetricsOnCompletion() {
        var id = UUID.randomUUID();
        var record = new DeliberationRecord(id, UUID.randomUUID(), "NVDA",
                "IN_PROGRESS", "REGIME_CHANGED", "rule:momentum@v1",
                Instant.now());
        repository.persist(record);
        em.flush();

        record.setStatus("COMPLETED");
        record.setConvergenceState("CONSENSUS");
        record.setConfidence(0.92);
        record.setEstablishedCount(5);
        record.setDisputedCount(1);
        record.setPendingCount(0);
        record.setRounds(4);
        record.setEndedAt(Instant.now());
        record.setSummary("All agents agreed on BUY");
        repository.merge(record);
        em.flush();

        var found = repository.findById(id).orElseThrow();
        assertEquals("COMPLETED", found.getStatus());
        assertEquals(5, found.getEstablishedCount());
        assertEquals(1, found.getDisputedCount());
        assertEquals(4, found.getRounds());
    }
}
