package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class MonitorAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "monitor",
                "status", "completed",
                "agentType", "rule-based",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "description", "Enhanced monitoring thresholds set for affected instruments");
    }
}
