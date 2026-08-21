package io.casehub.fsitrading.model;

import java.util.UUID;

public record IncidentResolvedEvent(
        UUID caseId,
        IncidentSeverity severity,
        String resolution) {
}
