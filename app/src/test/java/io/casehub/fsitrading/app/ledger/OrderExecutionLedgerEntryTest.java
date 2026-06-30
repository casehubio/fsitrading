package io.casehub.fsitrading.app.ledger;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderExecutionLedgerEntryTest {

    @Test
    void domainContentBytesDeterministic() {
        var entry = createEntry();
        var bytes1 = entry.domainContentBytes();
        var bytes2 = entry.domainContentBytes();
        assertArrayEquals(bytes1, bytes2);
    }

    @Test
    void domainContentBytesIncludesAllFields() {
        var orderId = UUID.randomUUID();
        var strategyId = UUID.randomUUID();
        var entry = new OrderExecutionLedgerEntry();
        entry.orderId = orderId;
        entry.instrument = "AAPL";
        entry.side = "BUY";
        entry.quantity = new BigDecimal("100.5");
        entry.fillPrice = new BigDecimal("150.25");
        entry.strategyId = strategyId;

        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains(orderId.toString()));
        assertTrue(content.contains("AAPL"));
        assertTrue(content.contains("BUY"));
        assertTrue(content.contains("100.5"));
        assertTrue(content.contains("150.25"));
        assertTrue(content.contains(strategyId.toString()));
    }

    @Test
    void domainContentBytesPipeDelimited() {
        var entry = createEntry();
        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertEquals(5, content.chars().filter(c -> c == '|').count());
    }

    @Test
    void domainContentBytesHandlesNullFields() {
        var entry = new OrderExecutionLedgerEntry();
        var content = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);
        assertEquals("|||||", content);
    }

    @Test
    void discriminatorValueIsOrderExecution() {
        var dv = OrderExecutionLedgerEntry.class
                .getAnnotation(jakarta.persistence.DiscriminatorValue.class);
        assertNotNull(dv);
        assertEquals("ORDER_EXECUTION", dv.value());
    }

    private OrderExecutionLedgerEntry createEntry() {
        var entry = new OrderExecutionLedgerEntry();
        entry.orderId = UUID.randomUUID();
        entry.instrument = "MSFT";
        entry.side = "SELL";
        entry.quantity = new BigDecimal("50");
        entry.fillPrice = new BigDecimal("420.00");
        entry.strategyId = UUID.randomUUID();
        return entry;
    }
}
