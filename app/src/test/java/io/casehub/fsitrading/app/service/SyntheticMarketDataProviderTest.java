package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.model.MarketEventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SyntheticMarketDataProviderTest {

    @Inject
    SyntheticMarketDataProvider provider;

    @Test
    void generateTick_producesValidEvent() {
        var event = provider.generateTick();

        assertNotNull(event.getId());
        assertNotNull(event.getInstrument());
        assertEquals(MarketEventType.PRICE_TICK, event.getEventType());
        assertTrue(event.getPrice().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(event.getVolume());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void generateTick_instrumentFromKnownSet() {
        var knownSymbols = SyntheticMarketDataProvider.INSTRUMENTS.stream()
                .map(SyntheticMarketDataProvider.SyntheticInstrument::symbol)
                .toList();

        for (int i = 0; i < 20; i++) {
            var event = provider.generateTick();
            assertTrue(knownSymbols.contains(event.getInstrument()),
                    "Unknown instrument: " + event.getInstrument());
        }
    }

    @Test
    void findRecent_returnsGeneratedTicks() {
        provider.generateTick();
        provider.generateTick();

        var recent = provider.findRecent(10);

        assertFalse(recent.isEmpty());
    }
}
