package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.spi.FsiCbrFeatureExtractor;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class FlashCrashFeatureExtractor implements FsiCbrFeatureExtractor {

    @Override
    public String caseType() {
        return "flash-crash";
    }

    @Override
    public Map<String, Object> extractFromSnapshot(Map<String, Object> snapshot,
                                                    Instant detectedAt) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("event_type", "FLASH_CRASH");
        features.put("instrument", snapshot.get("instrument"));
        features.put("price_drop_pct", snapshot.getOrDefault("priceDropPct", 0.0));
        features.put("drop_duration_seconds", snapshot.getOrDefault("dropDurationSeconds", 0));
        features.put("volume_spike_ratio", snapshot.getOrDefault("volumeSpikeRatio", 1.0));
        features.put("market_regime", snapshot.getOrDefault("regime", "VOLATILE"));
        double hour = detectedAt.atZone(ZoneOffset.UTC).getHour()
                      + detectedAt.atZone(ZoneOffset.UTC).getMinute() / 60.0;
        features.put("time_of_day", hour);
        features.put("affected_instruments_count",
                snapshot.getOrDefault("affectedInstrumentsCount", 1));
        return features;
    }
}
