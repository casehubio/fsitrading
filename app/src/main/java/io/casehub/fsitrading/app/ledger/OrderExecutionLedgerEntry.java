package io.casehub.fsitrading.app.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "order_execution_ledger_entry")
@DiscriminatorValue("ORDER_EXECUTION")
public class OrderExecutionLedgerEntry extends LedgerEntry {

    @Column(name = "order_id", nullable = false)
    public UUID orderId;

    @Column(name = "instrument", nullable = false, length = 50)
    public String instrument;

    @Column(name = "side", nullable = false, length = 10)
    public String side;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    public BigDecimal quantity;

    @Column(name = "fill_price", nullable = false, precision = 19, scale = 8)
    public BigDecimal fillPrice;

    @Column(name = "strategy_id", nullable = false)
    public UUID strategyId;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                orderId != null ? orderId.toString() : "",
                instrument != null ? instrument : "",
                side != null ? side : "",
                quantity != null ? quantity.toPlainString() : "",
                fillPrice != null ? fillPrice.toPlainString() : "",
                strategyId != null ? strategyId.toString() : ""
        ).getBytes(StandardCharsets.UTF_8);
    }
}
