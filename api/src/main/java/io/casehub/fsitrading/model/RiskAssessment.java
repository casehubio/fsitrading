package io.casehub.fsitrading.model;

import java.util.Map;

public record RiskAssessment(Level level, Map<String, Level> perInstrument) {

    public enum Level {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public boolean requiresApproval() {
        return level == Level.HIGH || level == Level.CRITICAL;
    }
}
