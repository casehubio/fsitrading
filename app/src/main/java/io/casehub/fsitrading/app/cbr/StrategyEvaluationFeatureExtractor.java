package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.spi.FsiCbrFeatureExtractor;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class StrategyEvaluationFeatureExtractor implements FsiCbrFeatureExtractor {

    @Override
    public String caseType() {
        return "strategy-evaluation";
    }

    @Override
    public Map<String, Object> extractFromSnapshot(Map<String, Object> snapshot,
                                                    Instant detectedAt) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("event_type", "STRATEGY_EVALUATION");
        features.put("instrument", snapshot.get("instrument"));
        features.put("strategy_name", snapshot.getOrDefault("strategyName", "unknown"));
        features.put("market_regime", snapshot.getOrDefault("regime", "UNKNOWN"));
        features.put("confidence", snapshot.getOrDefault("confidence", 0.0));
        features.put("action", snapshot.getOrDefault("action", "HOLD"));
        double hour = detectedAt.atZone(ZoneOffset.UTC).getHour()
                      + detectedAt.atZone(ZoneOffset.UTC).getMinute() / 60.0;
        features.put("time_of_day", hour);
        features.put("quorum_result", snapshot.getOrDefault("quorumResult", "AGREE"));
        return features;
    }
}
