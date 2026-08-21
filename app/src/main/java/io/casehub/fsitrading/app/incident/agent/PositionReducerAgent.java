package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class PositionReducerAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "reduce-exposure",
                "status", "completed",
                "agentType", "llm",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "reductionPercent", 50,
                "description", "Position exposure reduced by 50% across affected instruments (deterministic fallback)");
    }
}
