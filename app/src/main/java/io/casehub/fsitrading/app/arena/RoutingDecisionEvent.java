package io.casehub.fsitrading.app.arena;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoutingDecisionEvent(
        UUID evaluationId,
        String instrument,
        List<String> selectedAgents,
        String routingStrategy,
        Instant decidedAt) {}
