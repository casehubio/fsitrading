package io.casehub.fsitrading.app.cbr;

import io.casehub.api.context.CaseContext;
import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.app.service.SyntheticMarketDataProvider;
import io.casehub.fsitrading.model.MarketEventType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiFeatureExtractor {

    private static final int MAX_EVENTS    = 30;
    private static final int VOLUME_WINDOW = 10;

    private final SyntheticMarketDataProvider marketData;

    @Inject
    public FsiFeatureExtractor(SyntheticMarketDataProvider marketData) {
        this.marketData = marketData;
    }

    public Map<String, Object> extract(CaseContext context) {
        String instrument = context.getString("instrument");
        List<MarketEventEntity> events =
                marketData.findRecentByInstrument(instrument, MAX_EVENTS);
        return buildFeatures(context, events);
    }

    public Map<String, Object> extractBefore(CaseContext context, Instant before) {
        String instrument = context.getString("instrument");
        List<MarketEventEntity> events =
                marketData.findRecentByInstrumentBefore(instrument, before, MAX_EVENTS);
        return buildFeatures(context, events);
    }

    public Map<String, Object> extractFromSnapshot(Map<String, Object> snapshot, Instant detectedAt) {
        String instrument = (String) snapshot.get("instrument");
        String eventType  = (String) snapshot.get("eventType");
        String sector     = (String) snapshot.get("sector");
        List<MarketEventEntity> events =
                marketData.findRecentByInstrumentBefore(instrument, detectedAt, MAX_EVENTS);
        return buildFeaturesFromParams(eventType, sector, detectedAt, events);
    }


    private Map<String, Object> buildFeatures(CaseContext context,
                                              List<MarketEventEntity> events) {
        String  eventType  = context.getString("eventType");
        String  sector     = context.getString("sector");
        String  detectedAt = context.getString("detectedAt");
        Instant detection  = Instant.parse(detectedAt);
        return buildFeaturesFromParams(eventType, sector, detection, events);
    }

    private Map<String, Object> buildFeaturesFromParams(String eventType, String sector,
                                                        Instant detection,
                                                        List<MarketEventEntity> events) {
        Map<String, Object> features = new LinkedHashMap<>();

        features.put("event_type", eventType);
        features.put("instrument_sector", sector);

        double hour = detection.atZone(ZoneOffset.UTC).getHour()
                      + detection.atZone(ZoneOffset.UTC).getMinute() / 60.0;
        features.put("time_of_day", hour);

        List<MarketEventEntity> priceTicks = events.stream()
                                                   .filter(e -> e.getEventType() == MarketEventType.PRICE_TICK)
                                                   .toList();

        features.put("volatility_at_detection", computeVolatility(priceTicks));

        features.put("volume_profile", priceTicks.stream()
                                                 .limit(VOLUME_WINDOW)
                                                 .map(e -> e.getVolume() != null ? e.getVolume().doubleValue() : 0.0)
                                                 .toList());

        features.put("price_action_pattern", priceTicks.stream()
                                                       .limit(MAX_EVENTS)
                                                       .map(this::toTimeSeriesPoint)
                                                       .toList());

        features.put("event_sequence", events.stream()
                                             .map(e -> e.getEventType().name())
                                             .toList());

        return features;
    }


    private double computeVolatility(List<MarketEventEntity> priceTicks) {
        if (priceTicks.size() < 2) {return 0.0;}
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < priceTicks.size(); i++) {
            double prev = priceTicks.get(i - 1).getPrice().doubleValue();
            double curr = priceTicks.get(i).getPrice().doubleValue();
            if (prev > 0) {returns.add((curr - prev) / prev);}
        }
        if (returns.isEmpty()) {return 0.0;}
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                                 .mapToDouble(r -> (r - mean) * (r - mean))
                                 .average().orElse(0);
        return Math.sqrt(variance) * 100;
    }

    private Map<String, Object> toTimeSeriesPoint(MarketEventEntity e) {
        return Map.of(
                "timestamp", (double) e.getOccurredAt().toEpochMilli(),
                "price", e.getPrice().doubleValue(),
                "momentum", 0.0);
    }
}
