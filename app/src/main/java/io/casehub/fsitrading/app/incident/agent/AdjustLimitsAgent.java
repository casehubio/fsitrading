package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class AdjustLimitsAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "adjust-limits",
                "status", "completed",
                "agentType", "rule-based",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "description", "Position limits tightened for affected instruments");
    }
}
