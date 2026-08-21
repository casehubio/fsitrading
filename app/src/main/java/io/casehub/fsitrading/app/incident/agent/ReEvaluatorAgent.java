package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class ReEvaluatorAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "re-evaluate",
                "status", "completed",
                "agentType", "llm",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "description", "Gap open impact assessed — monitoring positions (deterministic fallback)");
    }
}
