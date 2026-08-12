package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.model.PriceTick;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SyntheticMarketDataProviderTest {

    private final SyntheticMarketDataProvider provider = new SyntheticMarketDataProvider();

    @Test
    void generateTick_returnsPriceTick() {
        PriceTick tick = provider.generateTick();

        assertNotNull(tick.instrument());
        assertTrue(tick.price().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(tick.volume());
        assertTrue(tick.volume().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(tick.timestamp());
        assertFalse(tick.anomaly());
    }

    @Test
    void generateTick_instrumentFromKnownSet() {
        var knownSymbols = SyntheticMarketDataProvider.INSTRUMENTS.stream()
                .map(SyntheticMarketDataProvider.SyntheticInstrument::symbol)
                .toList();

        for (int i = 0; i < 20; i++) {
            PriceTick tick = provider.generateTick();
            assertTrue(knownSymbols.contains(tick.instrument()),
                    "Unknown instrument: " + tick.instrument());
        }
    }

    @Test
    void generateTick_volumeIsPositive() {
        for (int i = 0; i < 10; i++) {
            PriceTick tick = provider.generateTick();
            assertTrue(tick.volume().compareTo(BigDecimal.ZERO) > 0);
        }
    }
}
