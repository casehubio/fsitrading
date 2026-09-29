package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.spi.FsiCbrFeatureExtractor;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class RiskEscalationFeatureExtractor implements FsiCbrFeatureExtractor {

    @Override
    public String caseType() {
        return "risk-escalation";
    }

    @Override
    public Map<String, Object> extractFromSnapshot(Map<String, Object> snapshot,
                                                    Instant detectedAt) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("event_type", "RISK_ESCALATION");
        features.put("instrument", snapshot.get("instrument"));
        features.put("risk_level", snapshot.getOrDefault("riskLevel", "HIGH"));
        features.put("var_amount", snapshot.getOrDefault("varAmount", 0.0));
        features.put("exposure_pct", snapshot.getOrDefault("exposurePct", 0.0));
        features.put("market_regime", snapshot.getOrDefault("regime", "UNKNOWN"));
        double hour = detectedAt.atZone(ZoneOffset.UTC).getHour()
                      + detectedAt.atZone(ZoneOffset.UTC).getMinute() / 60.0;
        features.put("time_of_day", hour);
        features.put("escalation_severity",
                snapshot.getOrDefault("escalationSeverity", "HIGH"));
        return features;
    }
}
