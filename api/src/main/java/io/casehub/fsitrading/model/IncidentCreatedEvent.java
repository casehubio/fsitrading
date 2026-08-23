package io.casehub.fsitrading.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentCreatedEvent(
        UUID caseId,
        IncidentSeverity severity,
        MarketEventType eventType,
        List<String> instruments,
        String description,
        Instant createdAt,
        Instant claimDeadline,
        Instant completionDeadline) {
}
