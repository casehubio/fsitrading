package io.casehub.fsitrading.app.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "strategy_evaluation_ledger_entry")
@DiscriminatorValue("STRATEGY_EVALUATION")
public class StrategyEvaluationLedgerEntry extends LedgerEntry {

    @Column(name = "strategy_id", nullable = false)
    public UUID strategyId;

    @Column(name = "strategy_name", nullable = false, length = 255)
    public String strategyName;

    @Column(name = "instrument", nullable = false, length = 50)
    public String instrument;

    @Column(name = "signal", nullable = false, length = 20)
    public String signal;

    @Column(name = "rationale", columnDefinition = "text")
    public String rationale;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                strategyId != null ? strategyId.toString() : "",
                strategyName != null ? strategyName : "",
                instrument != null ? instrument : "",
                signal != null ? signal : "",
                rationale != null ? rationale : ""
        ).getBytes(StandardCharsets.UTF_8);
    }
}
