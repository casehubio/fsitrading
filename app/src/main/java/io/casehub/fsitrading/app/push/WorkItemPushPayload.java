package io.casehub.fsitrading.app.push;

import java.time.Instant;
import java.util.UUID;

public sealed interface WorkItemPushPayload {

    String type();

    record WorkItemCreated(
            String type,
            UUID itemId,
            String title,
            String itemType,
            String candidateGroups,
            String status,
            Instant claimDeadline,
            Instant expiresAt,
            Instant createdAt) implements WorkItemPushPayload {
        public WorkItemCreated(UUID itemId, String title, String itemType,
                               String candidateGroups, String status,
                               Instant claimDeadline, Instant expiresAt,
                               Instant createdAt) {
            this("WORK_ITEM_CREATED", itemId, title, itemType, candidateGroups,
                 status, claimDeadline, expiresAt, createdAt);
        }
    }

    record WorkItemAssigned(
            String type,
            UUID itemId,
            String assignedTo,
            Instant assignedAt) implements WorkItemPushPayload {
        public WorkItemAssigned(UUID itemId, String assignedTo, Instant assignedAt) {
            this("WORK_ITEM_ASSIGNED", itemId, assignedTo, assignedAt);
        }
    }

    record GateOpened(
            String type,
            UUID caseId,
            String actionDescription,
            String riskLevel,
            String candidateGroups) implements WorkItemPushPayload {
        public GateOpened(UUID caseId, String actionDescription,
                          String riskLevel, String candidateGroups) {
            this("GATE_OPENED", caseId, actionDescription,
                 riskLevel, candidateGroups);
        }
    }

    record WorkItemEscalated(
            String type,
            UUID itemId,
            String candidateGroups,
            Instant claimDeadline,
            Instant expiresAt) implements WorkItemPushPayload {
        public WorkItemEscalated(UUID itemId, String candidateGroups,
                                 Instant claimDeadline, Instant expiresAt) {
            this("WORK_ITEM_ESCALATED", itemId, candidateGroups,
                 claimDeadline, expiresAt);
        }
    }

    record WorkItemCompleted(
            String type,
            UUID itemId,
            String outcome,
            String resolution,
            Instant completedAt) implements WorkItemPushPayload {
        public WorkItemCompleted(UUID itemId, String outcome,
                                 String resolution, Instant completedAt) {
            this("WORK_ITEM_COMPLETED", itemId, outcome, resolution, completedAt);
        }
    }
}
