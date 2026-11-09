package io.casehub.fsitrading.app.view;

import io.casehub.fsitrading.app.model.ArenaRunEntity;

import java.time.Instant;
import java.util.UUID;

public record RoutingDecisionView(UUID id, String instrument, String status,
                                  UUID idempotencyKey, String resultJson,
                                  String reason, Instant createdAt,
                                  Instant completedAt) {

    public static RoutingDecisionView toView(ArenaRunEntity e) {
        return new RoutingDecisionView(e.getId(), e.getInstrument(), e.getStatus(),
                e.getIdempotencyKey(), e.getResultJson(),
                e.getReason(), e.getCreatedAt(),
                e.getCompletedAt());
    }
}
