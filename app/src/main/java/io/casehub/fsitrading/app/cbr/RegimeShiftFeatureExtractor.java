package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.spi.FsiCbrFeatureExtractor;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class RegimeShiftFeatureExtractor implements FsiCbrFeatureExtractor {

    @Override
    public String caseType() {
        return "regime-shift";
    }

    @Override
    public Map<String, Object> extractFromSnapshot(Map<String, Object> snapshot,
                                                    Instant detectedAt) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("event_type", "REGIME_SHIFT");
        features.put("instrument", snapshot.get("instrument"));
        features.put("old_regime", snapshot.getOrDefault("oldRegime", "UNKNOWN"));
        features.put("new_regime", snapshot.getOrDefault("newRegime", "UNKNOWN"));
        features.put("transition_duration_minutes",
                snapshot.getOrDefault("transitionDurationMinutes", 0));
        features.put("market_regime", snapshot.getOrDefault("newRegime", "UNKNOWN"));
        double hour = detectedAt.atZone(ZoneOffset.UTC).getHour()
                      + detectedAt.atZone(ZoneOffset.UTC).getMinute() / 60.0;
        features.put("time_of_day", hour);
        features.put("volatility_change_pct",
                snapshot.getOrDefault("volatilityChangePct", 0.0));
        return features;
    }
}
