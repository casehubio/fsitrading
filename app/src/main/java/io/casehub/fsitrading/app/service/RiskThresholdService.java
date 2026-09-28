package io.casehub.fsitrading.app.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RiskThresholdService {

    private final ConcurrentHashMap<ThresholdKey, BigDecimal> thresholds = new ConcurrentHashMap<>();

    public ThresholdAdjustment adjust(String instrument, String thresholdType, BigDecimal newValue) {
        var key = new ThresholdKey(instrument, thresholdType);
        var previous = thresholds.put(key, newValue);
        return new ThresholdAdjustment(instrument, thresholdType, previous, newValue);
    }

    public BigDecimal get(String instrument, String thresholdType) {
        return thresholds.get(new ThresholdKey(instrument, thresholdType));
    }

    private record ThresholdKey(String instrument, String thresholdType) {}

    public record ThresholdAdjustment(String instrument, String thresholdType,
                                      BigDecimal previousValue, BigDecimal newValue) {}
}
