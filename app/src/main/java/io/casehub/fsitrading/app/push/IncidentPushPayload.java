package io.casehub.fsitrading.app.push;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface IncidentPushPayload {

    String type();

    record IncidentCreated(
            String type,
            UUID caseId,
            String severity,
            String eventType,
            List<String> instruments,
            String description,
            Instant claimDeadline,
            Instant completionDeadline,
            Instant createdAt) implements IncidentPushPayload {
        public IncidentCreated(UUID caseId, String severity, String eventType,
                               List<String> instruments, String description,
                               Instant claimDeadline, Instant completionDeadline,
                               Instant createdAt) {
            this("INCIDENT_CREATED", caseId, severity, eventType, instruments,
                 description, claimDeadline, completionDeadline, createdAt);
        }
    }

    record SlaBreached(
            String type,
            UUID caseId,
            UUID taskId,
            String breachType,
            int tier,
            String severity) implements IncidentPushPayload {
        public SlaBreached(UUID caseId, UUID taskId, String breachType,
                           int tier, String severity) {
            this("SLA_BREACHED", caseId, taskId, breachType, tier, severity);
        }
    }

    record IncidentResolved(
            String type,
            UUID caseId,
            Instant resolvedAt) implements IncidentPushPayload {
        public IncidentResolved(UUID caseId, Instant resolvedAt) {
            this("INCIDENT_RESOLVED", caseId, resolvedAt);
        }
    }
}
