package io.casehub.fsitrading;

import io.casehub.fsitrading.model.StrategyType;

import java.util.Objects;

public final class FsiActorIdentity {

    private FsiActorIdentity() {}

    public static String forStrategy(StrategyType type) {
        Objects.requireNonNull(type, "type");
        return "rule:" + toKebabCase(type.name()) + "@v1";
    }

    public static String actorRole(StrategyType type) {
        Objects.requireNonNull(type, "type");
        return toKebabCase(type.name()) + "-strategy";
    }

    public static String capabilityTag(StrategyType type) {
        Objects.requireNonNull(type, "type");
        return toKebabCase(type.name());
    }

    private static String toKebabCase(String enumName) {
        return enumName.toLowerCase().replace('_', '-');
    }
}
