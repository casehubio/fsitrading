package io.casehub.fsitrading.app.deliberation;

import java.time.Instant;
import java.util.UUID;

public record DeliberationFailedEvent(
        UUID deliberationId,
        UUID channelId,
        String instrument,
        String reason,
        Instant endedAt) {}
