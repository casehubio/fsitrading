package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

public class AlertOncallAgent implements IncidentResponseAgent {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        return Map.of(
                "action", "alert-oncall",
                "status", "completed",
                "agentType", "rule-based",
                "instruments", input.getOrDefault("instruments", java.util.List.of()),
                "alertTarget", "fsi-oncall",
                "severity", input.getOrDefault("severity", "UNKNOWN"),
                "description", "On-call trader notified — severity " + input.getOrDefault("severity", "UNKNOWN"));
    }
}
