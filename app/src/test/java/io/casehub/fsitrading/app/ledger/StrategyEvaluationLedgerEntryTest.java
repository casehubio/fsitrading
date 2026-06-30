package io.casehub.fsitrading.app.ledger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StrategyEvaluationLedgerEntryTest {

    @Test
    void domainContentBytesDeterministic() {
        var entry = createEntry();
        var bytes1 = entry.domainContentBytes();
        var bytes2 = entry.domainContentBytes();
        assertArrayEquals(bytes1, bytes2);
    }

    @Test
    void domainContentBytesIncludesAllFields() {
        var strategyId = UUID.randomUUID();
        var entry = new StrategyEvaluationLedgerEntry();
        entry.strategyId = strategyId;
        entry.strategyName = "momentum-v1";
        entry.instrument = "NVDA";
        entry.signal = "BUY";
        entry.rationale = "Price crossed 20-day SMA";

        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains(strategyId.toString()));
        assertTrue(content.contains("momentum-v1"));
        assertTrue(content.contains("NVDA"));
        assertTrue(content.contains("BUY"));
        assertTrue(content.contains("Price crossed 20-day SMA"));
    }

    @Test
    void domainContentBytesPipeDelimited() {
        var entry = createEntry();
        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertEquals(4, content.chars().filter(c -> c == '|').count());
    }

    @Test
    void domainContentBytesHandlesNullFields() {
        var entry = new StrategyEvaluationLedgerEntry();
        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertEquals("||||", content);
    }

    @Test
    void discriminatorValueIsStrategyEvaluation() {
        var dv = StrategyEvaluationLedgerEntry.class
                .getAnnotation(jakarta.persistence.DiscriminatorValue.class);
        assertNotNull(dv);
        assertEquals("STRATEGY_EVALUATION", dv.value());
    }

    private StrategyEvaluationLedgerEntry createEntry() {
        var entry = new StrategyEvaluationLedgerEntry();
        entry.strategyId = UUID.randomUUID();
        entry.strategyName = "mean-reversion-v2";
        entry.instrument = "GOOGL";
        entry.signal = "SELL";
        entry.rationale = "RSI above 70, overbought signal";
        return entry;
    }
}
