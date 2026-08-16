package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.conversation.CommonGroundState;
import io.casehub.blocks.conversation.ConvergenceSignal;
import io.casehub.blocks.conversation.ConvergenceState;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.EpistemicStatus;
import io.casehub.blocks.conversation.GroundedFact;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FsiDeliberationOrchestratorTest {

    @Inject
    FsiDeliberationOrchestrator orchestrator;

    @Inject
    DeliberationRecordRepository repository;

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void startDeliberationCreatesInProgressRecord() {
        var recordId = orchestrator.startDeliberation("AAPL", "REGIME_CHANGED",
                List.of("rule:momentum@v1", "rule:mean-reversion@v1"));

        assertNotNull(recordId);
        em.flush();

        var record = repository.findById(recordId).orElseThrow();
        assertEquals("IN_PROGRESS", record.getStatus());
        assertEquals("AAPL", record.getInstrument());
        assertEquals("REGIME_CHANGED", record.getTriggerType());
    }

    @Test
    @Transactional
    void completeWithConsensus_createsTradeAndUpdatesRecord() {
        var recordId = orchestrator.startDeliberation("MSFT", "TREND_REVERSAL",
                List.of("rule:momentum@v1"));
        em.flush();

        var signal = new ConvergenceSignal(ConvergenceState.CONSENSUS, 0.92, "all agreed");
        var cg = new CommonGroundState(
                Map.of("p1", new GroundedFact("p1", "debate",
                        EpistemicStatus.ESTABLISHED, "BUY 200 MSFT at market",
                        Set.of("agent1", "agent2"), Set.of(), 1)),
                Map.of(), Map.of());
        var cs = new ConversationState(Map.of(), List.of(), List.of(), Map.of());

        orchestrator.completeDeliberation(recordId, signal, cg, cs);
        em.flush();
        em.clear();

        var record = repository.findById(recordId).orElseThrow();
        assertEquals("COMPLETED", record.getStatus());
        assertEquals("CONSENSUS", record.getConvergenceState());
        assertNotNull(record.getTradeDecisionId());
        assertNotNull(record.getEndedAt());
        assertNotNull(record.getConversationStateSnapshot());
        assertNotNull(record.getCommonGroundSnapshot());
        assertTrue(record.getCommonGroundSnapshot().contains("establishedFacts"));
    }

    @Test
    @Transactional
    void completeWithDeadlock_escalatesNoTrade() {
        var recordId = orchestrator.startDeliberation("TSLA", "MANUAL",
                List.of("rule:momentum@v1"));
        em.flush();

        var signal = new ConvergenceSignal(ConvergenceState.DEADLOCK, 0.0, "disagreement");
        var cg = new CommonGroundState(Map.of(), Map.of(),
                Map.of("d1", new GroundedFact("d1", "debate",
                        EpistemicStatus.DISPUTED, "BUY 100 TSLA",
                        Set.of(), Set.of("agent1"), 1)));
        var cs = new ConversationState(Map.of(), List.of(), List.of(), Map.of());

        orchestrator.completeDeliberation(recordId, signal, cg, cs);
        em.flush();
        em.clear();

        var record = repository.findById(recordId).orElseThrow();
        assertEquals("COMPLETED", record.getStatus());
        assertEquals("DEADLOCK", record.getConvergenceState());
        assertNull(record.getTradeDecisionId());
    }

    @Test
    void wallClockTimeout_marksRecordFailedAndReleasesGuard() {
        var recordId = orchestrator.startDeliberation("TIMEOUT", "REGIME_CHANGED",
                List.of("rule:momentum@v1"));

        var neverCompletingDebate = new CompletableFuture<Void>();
        orchestrator.executeWithTimeout(recordId, neverCompletingDebate, Duration.ofMillis(100));

        var record = repository.findById(recordId).orElseThrow();
        assertEquals("FAILED", record.getStatus());
        assertTrue(record.getSummary().contains("Wall-clock timeout"));
        assertNotNull(record.getEndedAt());

        assertTrue(repository.findInProgress("TIMEOUT").isEmpty(),
                "Guard must be released — no IN_PROGRESS record for this instrument");
    }

    @Test
    void wallClockTimeout_allowsNewDeliberationAfterCleanup() {
        var recordId = orchestrator.startDeliberation("RETRY", "REGIME_CHANGED",
                List.of("rule:momentum@v1"));

        orchestrator.executeWithTimeout(recordId, new CompletableFuture<>(), Duration.ofMillis(50));

        var newRecordId = orchestrator.startDeliberation("RETRY", "REGIME_CHANGED",
                List.of("rule:momentum@v1"));
        assertNotNull(newRecordId);
        assertNotEquals(recordId, newRecordId);
        assertTrue(repository.findInProgress("RETRY").isPresent());
    }

    @Test
    @Transactional
    void failDeliberation_setsFailedStatus() {
        var recordId = orchestrator.startDeliberation("NVDA", "REGIME_CHANGED",
                List.of("rule:momentum@v1"));
        em.flush();

        orchestrator.failDeliberation(recordId, "Wall-clock timeout");
        em.flush();
        em.clear();

        var record = repository.findById(recordId).orElseThrow();
        assertEquals("FAILED", record.getStatus());
        assertEquals("Wall-clock timeout", record.getSummary());
        assertNotNull(record.getEndedAt());
    }
}
