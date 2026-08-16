package io.casehub.fsitrading.app.deliberation;

import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.RegimeChanged;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendReversalDetected;
import io.casehub.fsitrading.model.TrendSummary;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FsiDeliberationTriggerTest {

    @Inject
    FsiDeliberationTrigger trigger;

    @Inject
    DeliberationRecordRepository repository;

    @Inject
    EntityManager em;

    private List<String> triggeredInstruments;

    @BeforeEach
    void setUp() {
        triggeredInstruments = new ArrayList<>();
        trigger.setDeliberationStarter(triggeredInstruments::add);
    }

    @Test
    void regimeChangeAlwaysTriggers() {
        var event = new RegimeChanged("AAPL", MarketRegime.QUIET, MarketRegime.VOLATILE,
                new RegimeAssessment("AAPL", MarketRegime.VOLATILE, 0.9, "regime shift", Instant.now()));

        trigger.onRegimeChange(event);

        assertEquals(1, triggeredInstruments.size());
        assertEquals("AAPL", triggeredInstruments.get(0));
    }

    @Test
    void trendReversalAboveThresholdTriggers() {
        var trend = new TrendSummary("MSFT", TrendDirection.DOWN, 0.8, 0.5, "NORMAL",
                Instant.now(), Instant.now());
        var event = new TrendReversalDetected("MSFT", TrendDirection.UP, TrendDirection.DOWN, trend);

        trigger.onTrendReversal(event);

        assertEquals(1, triggeredInstruments.size());
    }

    @Test
    void trendReversalBelowThresholdDoesNotTrigger() {
        var trend = new TrendSummary("GOOGL", TrendDirection.DOWN, 0.3, 0.2, "NORMAL",
                Instant.now(), Instant.now());
        var event = new TrendReversalDetected("GOOGL", TrendDirection.UP, TrendDirection.DOWN, trend);

        trigger.onTrendReversal(event);

        assertTrue(triggeredInstruments.isEmpty());
    }

    @Test
    @Transactional
    void concurrencyGuardDropsDuplicateTrigger() {
        var record = new DeliberationRecord(UUID.randomUUID(), UUID.randomUUID(), "NVDA",
                "IN_PROGRESS", "REGIME_CHANGED", "rule:momentum@v1", Instant.now());
        repository.persist(record);
        em.flush();

        var event = new RegimeChanged("NVDA", MarketRegime.QUIET, MarketRegime.VOLATILE,
                new RegimeAssessment("NVDA", MarketRegime.VOLATILE, 0.9, "regime shift", Instant.now()));
        trigger.onRegimeChange(event);

        assertTrue(triggeredInstruments.isEmpty());
    }

    @Test
    @Transactional
    void crashRecoverySetsInProgressToFailed() {
        var record = new DeliberationRecord(UUID.randomUUID(), UUID.randomUUID(), "AMZN",
                "IN_PROGRESS", "REGIME_CHANGED", "rule:momentum@v1", Instant.now());
        repository.persist(record);
        em.flush();

        trigger.recoverFromCrash();
        em.flush();
        em.clear();

        assertTrue(repository.findInProgress("AMZN").isEmpty());
    }

    @Test
    @Transactional
    void afterRecoveryNewDeliberationCanStart() {
        var record = new DeliberationRecord(UUID.randomUUID(), UUID.randomUUID(), "TSLA",
                "IN_PROGRESS", "REGIME_CHANGED", "rule:momentum@v1", Instant.now());
        repository.persist(record);
        em.flush();

        trigger.recoverFromCrash();
        em.flush();
        em.clear();

        var event = new RegimeChanged("TSLA", MarketRegime.QUIET, MarketRegime.VOLATILE,
                new RegimeAssessment("TSLA", MarketRegime.VOLATILE, 0.9, "regime shift", Instant.now()));
        trigger.onRegimeChange(event);

        assertEquals(1, triggeredInstruments.size());
        assertEquals("TSLA", triggeredInstruments.get(0));
    }
}
