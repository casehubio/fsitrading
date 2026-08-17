package io.casehub.fsitrading.app.deliberation;

import java.time.Instant;
import java.util.UUID;

public record DeliberationStartedEvent(
        UUID deliberationId,
        UUID channelId,
        String instrument,
        String triggerType,
        String participants,
        Instant startedAt) {}
