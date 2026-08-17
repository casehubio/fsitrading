package io.casehub.fsitrading.app.deliberation;

import java.time.Instant;
import java.util.UUID;

public record DeliberationCompletedEvent(
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
        Instant endedAt) {}
