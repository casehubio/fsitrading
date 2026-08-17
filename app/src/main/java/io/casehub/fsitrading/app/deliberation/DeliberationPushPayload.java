package io.casehub.fsitrading.app.deliberation;

import java.time.Instant;
import java.util.UUID;

public sealed interface DeliberationPushPayload {

    String type();

    record Started(
            String type,
            UUID deliberationId,
            UUID channelId,
            String instrument,
            String triggerType,
            Instant startedAt) implements DeliberationPushPayload {
        public Started(UUID deliberationId, UUID channelId, String instrument,
                       String triggerType, Instant startedAt) {
            this("DELIBERATION_STARTED", deliberationId, channelId,
                 instrument, triggerType, startedAt);
        }
    }

    record Completed(
            String type,
            UUID deliberationId,
            UUID channelId,
            String instrument,
            String convergenceState,
            double confidence,
            int establishedCount,
            int disputedCount,
            int pendingCount,
            int rounds,
            UUID commitmentId,
            UUID tradeDecisionId,
            String outcomeType,
            Instant endedAt) implements DeliberationPushPayload {
        public Completed(UUID deliberationId, UUID channelId, String instrument,
                         String convergenceState, double confidence,
                         int establishedCount, int disputedCount, int pendingCount,
                         int rounds, UUID commitmentId, UUID tradeDecisionId,
                         String outcomeType, Instant endedAt) {
            this("DELIBERATION_COMPLETED", deliberationId, channelId, instrument,
                 convergenceState, confidence, establishedCount, disputedCount,
                 pendingCount, rounds, commitmentId, tradeDecisionId,
                 outcomeType, endedAt);
        }
    }

    record Failed(
            String type,
            UUID deliberationId,
            UUID channelId,
            String instrument,
            String reason,
            Instant endedAt) implements DeliberationPushPayload {
        public Failed(UUID deliberationId, UUID channelId, String instrument,
                      String reason, Instant endedAt) {
            this("DELIBERATION_FAILED", deliberationId, channelId,
                 instrument, reason, endedAt);
        }
    }

    record ConvergenceUpdate(
            String type,
            UUID channelId,
            String convergenceState,
            double confidence,
            int establishedCount,
            int disputedCount,
            int pendingCount,
            int dispatchCount) implements DeliberationPushPayload {
        public ConvergenceUpdate(UUID channelId, String convergenceState,
                                 double confidence, int establishedCount,
                                 int disputedCount, int pendingCount,
                                 int dispatchCount) {
            this("CONVERGENCE_UPDATE", channelId, convergenceState, confidence,
                 establishedCount, disputedCount, pendingCount, dispatchCount);
        }
    }
}
