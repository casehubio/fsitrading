package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class HaltAndWaitAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "halt-and-wait",
                "status", "completed",
                "agentType", "rule-based",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "description", "Trading suspended — waiting for circuit breaker to lift");
    }
}
