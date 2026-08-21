package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class SentimentAnalyserAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "analyse-sentiment",
                "status", "completed",
                "agentType", "llm",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "sentiment", "negative",
                "description", "News event sentiment assessed as negative — recommending caution (deterministic fallback)");
    }
}
