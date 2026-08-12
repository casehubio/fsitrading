package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.Compactor;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.PriceTick;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FsiTickCompactor implements Compactor<PriceTick> {

    private static final int SIGMA_WINDOW = 100;
    private static final double SIGMA_THRESHOLD = 3.0;

    private final Map<String, Deque<BigDecimal>> priceHistory = new HashMap<>();

    @Override
    public List<LevelEvent<PriceTick>> compact(List<LevelEvent<PriceTick>> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        var deduped = deduplicateSameSecond(events);

        var result = new ArrayList<LevelEvent<PriceTick>>();
        for (var event : deduped) {
            var tick = event.payload();
            boolean anomaly = checkAnomaly(tick);
            if (anomaly && !tick.anomaly()) {
                var tagged = new PriceTick(tick.instrument(), tick.price(),
                        tick.volume(), tick.timestamp(), true);
                result.add(new LevelEvent<>(tagged, event.timestamp(), event.level()));
            } else {
                result.add(event);
            }
            updatePriceHistory(tick);
        }
        return result;
    }

    private List<LevelEvent<PriceTick>> deduplicateSameSecond(List<LevelEvent<PriceTick>> events) {
        var byKey = new LinkedHashMap<String, LevelEvent<PriceTick>>();
        for (var event : events) {
            var tick = event.payload();
            long epochSecond = tick.timestamp().getEpochSecond();
            String key = tick.instrument() + ":" + epochSecond;
            byKey.put(key, event);
        }
        return new ArrayList<>(byKey.values());
    }

    private boolean checkAnomaly(PriceTick tick) {
        var history = priceHistory.get(tick.instrument());
        if (history == null || history.size() < SIGMA_WINDOW) {
            return false;
        }

        double mean = history.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(0.0);
        double variance = history.stream()
                .mapToDouble(p -> {
                    double diff = p.doubleValue() - mean;
                    return diff * diff;
                })
                .average().orElse(0.0);
        double sigma = Math.sqrt(variance);
        if (sigma < 0.0001) {
            return false;
        }

        double deviation = Math.abs(tick.price().doubleValue() - mean) / sigma;
        return deviation > SIGMA_THRESHOLD;
    }

    private void updatePriceHistory(PriceTick tick) {
        var history = priceHistory.computeIfAbsent(tick.instrument(), k -> new LinkedList<>());
        history.addLast(tick.price());
        while (history.size() > SIGMA_WINDOW) {
            history.removeFirst();
        }
    }
}
