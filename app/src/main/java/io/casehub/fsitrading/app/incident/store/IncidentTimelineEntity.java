package io.casehub.fsitrading.app.incident.store;

import io.casehub.fsitrading.model.IncidentTimelineRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fsi_incident_timeline")
public class IncidentTimelineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false, length = 50)
    private String milestone;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(columnDefinition = "text")
    private String description;

    protected IncidentTimelineEntity() {}

    public static IncidentTimelineEntity from(UUID caseId, IncidentTimelineRecord record) {
        var entity = new IncidentTimelineEntity();
        entity.caseId = caseId;
        entity.milestone = record.milestone();
        entity.occurredAt = record.timestamp();
        entity.description = record.description();
        return entity;
    }

    public IncidentTimelineRecord toRecord() {
        return new IncidentTimelineRecord(milestone, occurredAt, description);
    }

    public UUID getCaseId() { return caseId; }
    public Instant getOccurredAt() { return occurredAt; }
}
