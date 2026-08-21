package io.casehub.fsitrading.app.incident.store;

import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.MarketEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fsi_incident")
public class IncidentEntity {

    @Id
    @Column(name = "case_id")
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private MarketEventType eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String instruments;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected IncidentEntity() {}

    public static IncidentEntity from(IncidentRecord record) {
        var entity = new IncidentEntity();
        entity.caseId = record.caseId();
        entity.severity = record.severity();
        entity.eventType = record.eventType();
        entity.instruments = String.join(",", record.instruments());
        entity.status = record.status();
        entity.createdAt = record.createdAt();
        entity.resolvedAt = record.resolvedAt();
        return entity;
    }

    public IncidentRecord toRecord() {
        List<String> instrumentList = instruments.isEmpty()
                                      ? List.of()
                                      : Arrays.asList(instruments.split(","));
        return new IncidentRecord(caseId, severity, eventType,
                                  instrumentList, status, createdAt, resolvedAt);}

    public UUID getCaseId() { return caseId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
