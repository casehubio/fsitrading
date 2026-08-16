package io.casehub.fsitrading.app.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "deliberation_decision_ledger_entry")
@DiscriminatorValue("DELIBERATION_DECISION")
public class DeliberationDecisionLedgerEntry extends JpaLedgerEntry {

    @Column(name = "deliberation_id", nullable = false)
    public UUID deliberationId;

    @Column(name = "channel_id", nullable = false)
    public UUID channelId;

    @Column(name = "instrument", nullable = false, length = 20)
    public String instrument;

    @Column(name = "convergence_state", nullable = false, length = 30)
    public String convergenceState;

    @Column(name = "confidence", nullable = false)
    public double confidence;

    @Column(name = "established_count", nullable = false)
    public int establishedCount;

    @Column(name = "disputed_count", nullable = false)
    public int disputedCount;

    @Column(name = "participants", nullable = false, length = 500)
    public String participants;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                deliberationId != null ? deliberationId.toString() : "",
                channelId != null ? channelId.toString() : "",
                instrument != null ? instrument : "",
                convergenceState != null ? convergenceState : "",
                String.valueOf(confidence),
                String.valueOf(establishedCount),
                String.valueOf(disputedCount),
                participants != null ? participants : ""
        ).getBytes(StandardCharsets.UTF_8);
    }
}
