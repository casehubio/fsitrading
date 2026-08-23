package io.casehub.fsitrading.model;

import java.time.Instant;
import java.util.UUID;

public record IncidentResolvedEvent(
        UUID caseId,
        IncidentSeverity severity,
        String resolution,
        Instant resolvedAt) {
}
