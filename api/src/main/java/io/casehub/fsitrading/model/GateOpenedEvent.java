package io.casehub.fsitrading.model;

import java.util.UUID;

public record GateOpenedEvent(
        UUID caseId,
        String actionDescription,
        String riskLevel,
        String candidateGroups) {
}
