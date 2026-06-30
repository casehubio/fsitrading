package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.model.MarketEventType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class SyntheticMarketDataProvider {

    private static final Logger log = Logger.getLogger(SyntheticMarketDataProvider.class);

    static final List<SyntheticInstrument> INSTRUMENTS = List.of(
            new SyntheticInstrument("AAPL", 175.00),
            new SyntheticInstrument("MSFT", 420.00),
            new SyntheticInstrument("GOOGL", 175.00),
            new SyntheticInstrument("AMZN", 185.00),
            new SyntheticInstrument("NVDA", 130.00)
    );

    @Inject
    EntityManager em;

    @Transactional
    public MarketEventEntity generateTick() {
        var random = ThreadLocalRandom.current();
        var synth = INSTRUMENTS.get(random.nextInt(INSTRUMENTS.size()));
        var pctChange = (random.nextDouble() - 0.5) * 0.04;
        var price = BigDecimal.valueOf(synth.basePrice * (1 + pctChange))
                .setScale(2, RoundingMode.HALF_UP);
        var volume = BigDecimal.valueOf(random.nextInt(1000, 50000));

        var event = new MarketEventEntity(
                UUID.randomUUID(), synth.symbol, MarketEventType.PRICE_TICK, price);
        event.setVolume(volume);
        em.persist(event);

        log.debugf("Synthetic tick: %s @ %s (vol: %s)", synth.symbol, price, volume);
        return event;
    }

    public List<MarketEventEntity> findRecent(int limit) {
        return em.createQuery(
                        "SELECT e FROM MarketEventEntity e ORDER BY e.occurredAt DESC",
                        MarketEventEntity.class)
                .setMaxResults(limit)
                .getResultList();
    }

    record SyntheticInstrument(String symbol, double basePrice) {}
}
