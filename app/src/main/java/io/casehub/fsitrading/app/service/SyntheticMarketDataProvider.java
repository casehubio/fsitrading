package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.model.PriceTick;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class SyntheticMarketDataProvider {

    static final List<SyntheticInstrument> INSTRUMENTS = List.of(
            new SyntheticInstrument("AAPL", 175.00),
            new SyntheticInstrument("MSFT", 420.00),
            new SyntheticInstrument("GOOGL", 175.00),
            new SyntheticInstrument("AMZN", 185.00),
            new SyntheticInstrument("NVDA", 130.00));

    @Inject
    EntityManager em;

    public PriceTick generateTick() {
        var random = ThreadLocalRandom.current();
        var synth = INSTRUMENTS.get(random.nextInt(INSTRUMENTS.size()));
        var pctChange = (random.nextDouble() - 0.5) * 0.04;
        var price = BigDecimal.valueOf(synth.basePrice * (1 + pctChange))
                .setScale(2, RoundingMode.HALF_UP);

        double fractionOfDay = fractionOfTradingDay();
        double volumeMultiplier = 1.0 + Math.abs(2.0 * fractionOfDay - 1.0);
        var volume = BigDecimal.valueOf(random.nextInt(1000, 50000) * volumeMultiplier)
                .setScale(0, RoundingMode.HALF_UP);

        return new PriceTick(synth.symbol, price, volume, Instant.now(), false);
    }

    public List<MarketEventEntity> findRecent(int limit) {
        return em.createQuery(
                        "SELECT e FROM MarketEventEntity e ORDER BY e.occurredAt DESC",
                        MarketEventEntity.class)
                .setMaxResults(limit)
                .getResultList();
    }

    private double fractionOfTradingDay() {
        var now = LocalTime.now(ZoneId.of("America/New_York"));
        var open = LocalTime.of(9, 30);
        var close = LocalTime.of(16, 0);
        if (now.isBefore(open) || now.isAfter(close)) {
            return 0.5;
        }
        long totalSeconds = open.until(close, ChronoUnit.SECONDS);
        long elapsed = open.until(now, ChronoUnit.SECONDS);
        return (double) elapsed / totalSeconds;
    }

    record SyntheticInstrument(String symbol, double basePrice) {}
}
