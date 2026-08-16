package io.casehub.fsitrading.app.ledger;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DeliberationDecisionLedgerEntryTest {

    @Inject
    TradingLedgerService ledgerService;

    @Test
    void recordDeliberationDecisionCreatesEntry() {
        var deliberationId = UUID.randomUUID();
        var channelId = UUID.randomUUID();

        var entryId = ledgerService.recordDeliberationDecision(
                deliberationId, channelId, "AAPL", "CONSENSUS",
                0.92, 5, 1, "rule:momentum@v1,rule:mean-reversion@v1", null);

        assertNotNull(entryId);
    }

    @Test
    void discriminatorValueIsDeliberationDecision() {
        var deliberationId = UUID.randomUUID();
        var channelId = UUID.randomUUID();

        ledgerService.recordDeliberationDecision(
                deliberationId, channelId, "MSFT", "CONSENSUS",
                0.85, 3, 0, "rule:momentum@v1", null);

        var entries = ledgerService.findByOrderId(deliberationId);
        assertFalse(entries.isEmpty());
        assertInstanceOf(DeliberationDecisionLedgerEntry.class, entries.get(0));
    }

    @Test
    void causedByEntryIdIsNullForDeliberationOriginated() {
        var deliberationId = UUID.randomUUID();
        var channelId = UUID.randomUUID();

        ledgerService.recordDeliberationDecision(
                deliberationId, channelId, "TSLA", "CONSENSUS",
                0.90, 4, 1, "rule:momentum@v1", null);

        var entries = ledgerService.findByOrderId(deliberationId);
        var entry = (DeliberationDecisionLedgerEntry) entries.get(0);
        assertNull(entry.causedByEntryId);
        assertEquals("CONSENSUS", entry.convergenceState);
        assertEquals(0.90, entry.confidence, 0.001);
        assertEquals(4, entry.establishedCount);
        assertEquals(1, entry.disputedCount);
    }
}
