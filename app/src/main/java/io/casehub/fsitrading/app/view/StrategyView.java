package io.casehub.fsitrading.app.view;

import io.casehub.fsitrading.app.model.StrategyEntity;
import io.casehub.fsitrading.model.StrategyType;

import java.time.Instant;
import java.util.UUID;

public record StrategyView(UUID id, String name, StrategyType strategyType,
                           String instruments, String parameters,
                           boolean active, Instant createdAt) {

    public static StrategyView toView(StrategyEntity e) {
        return new StrategyView(e.getId(), e.getName(), e.getStrategyType(),
                e.getInstruments(), e.getParameters(),
                e.isActive(), e.getCreatedAt());
    }
}
