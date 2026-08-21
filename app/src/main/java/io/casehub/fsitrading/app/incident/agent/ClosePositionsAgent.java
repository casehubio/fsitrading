package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class ClosePositionsAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "close-positions",
                "status", "completed",
                "agentType", "rule-based",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "description", "Market orders submitted to close all positions in affected instruments");
    }
}
