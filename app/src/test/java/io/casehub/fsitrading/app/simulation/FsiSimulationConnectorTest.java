package io.casehub.fsitrading.app.simulation;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FsiSimulationConnectorTest {

    private EventStreamBus<PriceTick> l0Bus;
    private List<PriceTick> capturedTicks;
    @SuppressWarnings("unchecked")
    private jakarta.enterprise.event.Event<RegimeChanged> regimeChangedEvent =
            mock(jakarta.enterprise.event.Event.class);
    private FsiSimulationConnector connector;

    @BeforeEach
    void setUp() {
        l0Bus = new EventStreamBus<>();
        capturedTicks = new ArrayList<>();
        l0Bus.subscribe(e -> true, le -> capturedTicks.add(le.payload()));
        connector = new FsiSimulationConnector(l0Bus, regimeChangedEvent);
    }

    @Test
    void dispatchesPriceTickToL0Bus() {
        connector.dispatch("price-tick", Map.of(
                "instrument", "AAPL",
                "price", "175.50",
                "volume", "12000"));

        assertThat(capturedTicks).hasSize(1);
        PriceTick tick = capturedTicks.getFirst();
        assertThat(tick.instrument()).isEqualTo("AAPL");
        assertThat(tick.price()).isEqualByComparingTo(new BigDecimal("175.50"));
        assertThat(tick.volume()).isEqualByComparingTo(new BigDecimal("12000"));
        assertThat(tick.anomaly()).isFalse();
    }

    @Test
    void dispatchesPriceTickWithAnomaly() {
        connector.dispatch("price-tick", Map.of(
                "instrument", "MSFT",
                "price", "380.00",
                "volume", "50000",
                "anomaly", true));

        assertThat(capturedTicks).hasSize(1);
        assertThat(capturedTicks.getFirst().anomaly()).isTrue();
    }

    @Test
    void dispatchesRegimeChangeAsCdiEvent() {
        connector.dispatch("regime-change", Map.of(
                "instrument", "SPY",
                "old-regime", "TRENDING",
                "new-regime", "VOLATILE"));

        var captor = org.mockito.ArgumentCaptor.forClass(RegimeChanged.class);
        verify(regimeChangedEvent).fire(captor.capture());
        RegimeChanged event = captor.getValue();
        assertThat(event.instrument()).isEqualTo("SPY");
        assertThat(event.oldRegime()).isEqualTo(MarketRegime.TRENDING);
        assertThat(event.newRegime()).isEqualTo(MarketRegime.VOLATILE);
    }

    @Test
    void rejectsUnknownAction() {
        assertThatThrownBy(() -> connector.dispatch("unknown", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void targetReturnsFsiMarketData() {
        assertThat(connector.target()).isEqualTo("fsi-market-data");
    }

    @Test
    void dispatchesMultipleTicksSequentially() {
        connector.dispatch("price-tick", Map.of(
                "instrument", "AAPL", "price", "175.00", "volume", "10000"));
        connector.dispatch("price-tick", Map.of(
                "instrument", "MSFT", "price", "420.00", "volume", "8000"));
        connector.dispatch("price-tick", Map.of(
                "instrument", "GOOGL", "price", "175.00", "volume", "6000"));

        assertThat(capturedTicks).hasSize(3);
        assertThat(capturedTicks).extracting(PriceTick::instrument)
                .containsExactly("AAPL", "MSFT", "GOOGL");
    }
}
