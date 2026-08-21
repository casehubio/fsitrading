package io.casehub.fsitrading.model;

import java.util.UUID;

public record SlaBreachEvent(
        UUID caseId,
        UUID taskId,
        String breachType,
        int tier,
        IncidentSeverity severity) {
}
