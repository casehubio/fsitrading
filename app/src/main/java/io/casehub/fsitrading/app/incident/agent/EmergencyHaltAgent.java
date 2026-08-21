package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class EmergencyHaltAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "emergency-halt",
                "status", "completed",
                "agentType", "rule-based",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "description", "Emergency halt executed — all trading suspended for affected instruments");
    }
}
