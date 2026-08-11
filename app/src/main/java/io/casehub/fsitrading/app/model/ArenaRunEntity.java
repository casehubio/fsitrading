package io.casehub.fsitrading.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "arena_run")
public class ArenaRunEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String instrument;

    @Column(nullable = false)
    private String status;

    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "result_json")
    private String resultJson;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ArenaRunEntity() {}

    public ArenaRunEntity(String instrument) {
        this.instrument = instrument;
        this.status = "IN_FLIGHT";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getInstrument() { return instrument; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public void complete(String resultJson) {
        this.status = "COMPLETED";
        this.resultJson = resultJson;
        this.completedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = "FAILED";
        this.reason = reason;
        this.completedAt = Instant.now();
    }

    public boolean isInFlight() {
        return "IN_FLIGHT".equals(status);
    }
}
