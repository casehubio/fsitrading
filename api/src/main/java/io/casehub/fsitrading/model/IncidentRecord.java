package io.casehub.fsitrading.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentRecord(
        UUID caseId,
        IncidentSeverity severity,
        MarketEventType eventType,
        List<String> instruments,
        String status,
        Instant createdAt,
        Instant resolvedAt) {
}
