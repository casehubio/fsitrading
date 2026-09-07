package io.casehub.fsitrading.app.cbr;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.app.pipeline.FsiEventLevels;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FsiRegimeSupersessionListenerTest {

    private CbrCaseMemoryStore cbrStore;
    private FsiRegimeSupersessionListener listener;
    private EventStreamBus<RegimeAssessment> l3Bus;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        l3Bus = new EventStreamBus<>();
        listener = new FsiRegimeSupersessionListener(cbrStore);
        listener.wire(l3Bus);
    }

    @Test
    void supersedesOnRegimeChange() {
        when(cbrStore.supersedeMatching(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(3);

        publish("AAPL", MarketRegime.TRENDING);
        publish("AAPL", MarketRegime.VOLATILE);

        var filtersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cbrStore).supersedeMatching(eq("default"), any(), anyString(),
                filtersCaptor.capture(), anyString());

        @SuppressWarnings("unchecked")
        Map<String, CbrFilter> filters = filtersCaptor.getValue();
        assertThat(filters).containsKey("market_regime");
    }

    @Test
    void doesNotSupersedeOnFirstAssessment() {
        publish("AAPL", MarketRegime.TRENDING);

        verify(cbrStore, never()).supersedeMatching(anyString(), any(), anyString(), any(), anyString());
    }

    @Test
    void doesNotSupersedeOnSameRegime() {
        publish("AAPL", MarketRegime.TRENDING);
        publish("AAPL", MarketRegime.TRENDING);

        verify(cbrStore, never()).supersedeMatching(anyString(), any(), anyString(), any(), anyString());
    }

    @Test
    void tracksPerInstrumentIndependently() {
        when(cbrStore.supersedeMatching(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(1);

        publish("AAPL", MarketRegime.TRENDING);
        publish("MSFT", MarketRegime.VOLATILE);
        publish("AAPL", MarketRegime.VOLATILE);

        verify(cbrStore).supersedeMatching(anyString(), any(), anyString(), any(), anyString());
    }

    private void publish(String instrument, MarketRegime regime) {
        l3Bus.publish(new LevelEvent<>(
                new RegimeAssessment(instrument, regime, 0.85, "test", Instant.now()),
                Instant.now().toEpochMilli(), FsiEventLevels.REGIME_1H, null));
    }
}
