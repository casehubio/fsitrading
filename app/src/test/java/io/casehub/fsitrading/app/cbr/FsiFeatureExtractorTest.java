package io.casehub.fsitrading.app.cbr;

import io.casehub.api.context.CaseContext;
import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.app.service.SyntheticMarketDataProvider;
import io.casehub.fsitrading.model.MarketEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FsiFeatureExtractorTest {

    private SyntheticMarketDataProvider marketData;
    private FsiFeatureExtractor extractor;
    private CaseContext context;

    @BeforeEach
    void setUp() {
        marketData = mock(SyntheticMarketDataProvider.class);
        extractor = new FsiFeatureExtractor(marketData);
        context = mock(CaseContext.class);
        when(context.getString("instrument")).thenReturn("AAPL");
        when(context.getString("eventType")).thenReturn("FLASH_CRASH");
        when(context.getString("sector")).thenReturn("EQUITY");
        when(context.getString("detectedAt")).thenReturn("2026-09-01T14:30:00Z");
    }

    @Test
    void extractsAllSevenFeatures() {
        stubMarketEvents("AAPL", 30);
        Map<String, Object> features = extractor.extract(context);
        assertThat(features).containsKeys(
                "event_type", "instrument_sector", "time_of_day",
                "volatility_at_detection", "volume_profile",
                "price_action_pattern", "event_sequence");
    }

    @Test
    void eventTypeFromContext() {
        stubMarketEvents("AAPL", 30);
        Map<String, Object> features = extractor.extract(context);
        assertThat(features.get("event_type")).isEqualTo("FLASH_CRASH");
    }

    @Test
    void timeOfDayFromDetectionTimestamp() {
        stubMarketEvents("AAPL", 30);
        Map<String, Object> features = extractor.extract(context);
        assertThat((double) features.get("time_of_day")).isBetween(14.0, 15.0);
    }

    @Test
    void volumeProfileHasUpToTenEntries() {
        stubMarketEvents("AAPL", 30);
        Map<String, Object> features = extractor.extract(context);
        @SuppressWarnings("unchecked")
        List<Double> volumes = (List<Double>) features.get("volume_profile");
        assertThat(volumes).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void priceActionPatternIsTimeSeries() {
        stubMarketEvents("AAPL", 30);
        Map<String, Object> features = extractor.extract(context);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pattern =
                (List<Map<String, Object>>) features.get("price_action_pattern");
        assertThat(pattern).isNotEmpty();
        assertThat(pattern.getFirst()).containsKeys("timestamp", "price", "momentum");
    }

    @Test
    void volatilityComputedFromPriceTickReturns() {
        stubMarketEvents("AAPL", 30);
        Map<String, Object> features = extractor.extract(context);
        double vol = (double) features.get("volatility_at_detection");
        assertThat(vol).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void extractBeforeUsesTimestampAnchor() {
        Instant before = Instant.parse("2026-09-01T14:30:00Z");
        stubMarketEventsBefore("AAPL", before, 30);
        Map<String, Object> features = extractor.extractBefore(context, before);
        verify(marketData).findRecentByInstrumentBefore("AAPL", before, 30);
        assertThat(features).containsKeys("event_type", "price_action_pattern");
    }

    @Test
    void filtersOnlyPriceTicksForVolatility() {
        List<MarketEventEntity> events = new ArrayList<>();
        events.add(makePriceTick("AAPL", 175.0, 1000));
        events.add(makePriceTick("AAPL", 176.0, 1100));
        events.add(makeEvent("AAPL", MarketEventType.NEWS_EVENT, 0.0, 0));
        events.add(makePriceTick("AAPL", 174.0, 900));
        when(marketData.findRecentByInstrument("AAPL", 30)).thenReturn(events);

        Map<String, Object> features = extractor.extract(context);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pattern =
                (List<Map<String, Object>>) features.get("price_action_pattern");
        assertThat(pattern).hasSize(3);
    }

    private void stubMarketEvents(String instrument, int count) {
        List<MarketEventEntity> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(makePriceTick(instrument, 175.0 + i * 0.1, 1000 + i * 100));
        }
        when(marketData.findRecentByInstrument(eq(instrument), anyInt())).thenReturn(events);
    }

    private void stubMarketEventsBefore(String instrument, Instant before, int count) {
        List<MarketEventEntity> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(makePriceTick(instrument, 175.0 + i * 0.1, 1000 + i * 100));
        }
        when(marketData.findRecentByInstrumentBefore(eq(instrument), eq(before), anyInt()))
                .thenReturn(events);
    }

    private MarketEventEntity makePriceTick(String instrument, double price, int volume) {
        return makeEvent(instrument, MarketEventType.PRICE_TICK, price, volume);
    }

    private MarketEventEntity makeEvent(String instrument, MarketEventType type,
                                         double price, int volume) {
        var e = new MarketEventEntity(UUID.randomUUID(), instrument, type,
                BigDecimal.valueOf(price));
        e.setVolume(BigDecimal.valueOf(volume));
        return e;
    }
}
