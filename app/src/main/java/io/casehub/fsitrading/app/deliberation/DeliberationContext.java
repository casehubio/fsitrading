package io.casehub.fsitrading.app.deliberation;

import java.util.List;
import java.util.UUID;

public record DeliberationContext(
        String instrument,
        String triggerType,
        String triggerSource,
        UUID channelId,
        List<String> agentIds) {}
