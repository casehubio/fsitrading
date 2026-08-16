package io.casehub.fsitrading.app.deliberation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deliberation_record")
public class DeliberationRecord {

    @Id
    private UUID id;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(nullable = false, length = 20)
    private String instrument;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    @Column(name = "convergence_state", length = 30)
    private String convergenceState;

    @Column
    private Double confidence;

    @Column(name = "established_count")
    private Integer establishedCount;

    @Column(name = "disputed_count")
    private Integer disputedCount;

    @Column(name = "pending_count")
    private Integer pendingCount;

    @Column(nullable = false)
    private int rounds;

    @Column(nullable = false, length = 500)
    private String participants;

    @Column(name = "commitment_id")
    private UUID commitmentId;

    @Column(name = "trade_decision_id")
    private UUID tradeDecisionId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "conversation_state_snapshot", columnDefinition = "TEXT")
    private String conversationStateSnapshot;

    @Column(name = "common_ground_snapshot", columnDefinition = "TEXT")
    private String commonGroundSnapshot;

    protected DeliberationRecord() {}

    public DeliberationRecord(UUID id, UUID channelId, String instrument,
                              String status, String triggerType, String participants,
                              Instant startedAt) {
        this.id = id;
        this.channelId = channelId;
        this.instrument = instrument;
        this.status = status;
        this.triggerType = triggerType;
        this.participants = participants;
        this.startedAt = startedAt;
    }

    public UUID getId() { return id; }
    public UUID getChannelId() { return channelId; }
    public String getInstrument() { return instrument; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTriggerType() { return triggerType; }
    public String getConvergenceState() { return convergenceState; }
    public void setConvergenceState(String convergenceState) { this.convergenceState = convergenceState; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Integer getEstablishedCount() { return establishedCount; }
    public void setEstablishedCount(Integer establishedCount) { this.establishedCount = establishedCount; }
    public Integer getDisputedCount() { return disputedCount; }
    public void setDisputedCount(Integer disputedCount) { this.disputedCount = disputedCount; }
    public Integer getPendingCount() { return pendingCount; }
    public void setPendingCount(Integer pendingCount) { this.pendingCount = pendingCount; }
    public int getRounds() { return rounds; }
    public void setRounds(int rounds) { this.rounds = rounds; }
    public String getParticipants() { return participants; }
    public UUID getCommitmentId() { return commitmentId; }
    public void setCommitmentId(UUID commitmentId) { this.commitmentId = commitmentId; }
    public UUID getTradeDecisionId() { return tradeDecisionId; }
    public void setTradeDecisionId(UUID tradeDecisionId) { this.tradeDecisionId = tradeDecisionId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getConversationStateSnapshot() { return conversationStateSnapshot; }
    public void setConversationStateSnapshot(String snapshot) { this.conversationStateSnapshot = snapshot; }
    public String getCommonGroundSnapshot() { return commonGroundSnapshot; }
    public void setCommonGroundSnapshot(String snapshot) { this.commonGroundSnapshot = snapshot; }
}
