package io.casehub.fsitrading.app.arena;

public record TrustScoreChangedEvent(
        String strategyType,
        String actorId,
        double trustScore,
        int decisionCount,
        String phase) {}
