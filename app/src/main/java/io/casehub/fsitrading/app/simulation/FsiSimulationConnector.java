package io.casehub.fsitrading.app.simulation;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.app.pipeline.FsiEventLevels;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeChanged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class FsiSimulationConnector {

    private final EventStreamBus<PriceTick> l0Bus;
    private final Event<RegimeChanged> regimeChangedEvent;

    @Inject
    public FsiSimulationConnector(
            @Named("l0Bus") EventStreamBus<PriceTick> l0Bus,
            Event<RegimeChanged> regimeChangedEvent) {
        this.l0Bus = l0Bus;
        this.regimeChangedEvent = regimeChangedEvent;
    }

    public String target() {
        return "fsi-market-data";
    }

    public void dispatch(String action, Map<String, Object> data) {
        switch (action) {
            case "price-tick" -> dispatchPriceTick(data);
            case "regime-change" -> dispatchRegimeChange(data);
            default -> throw new IllegalArgumentException("Unknown simulation action: " + action);
        }
    }

    private void dispatchPriceTick(Map<String, Object> data) {
        var tick = new PriceTick(
                (String) data.get("instrument"),
                new BigDecimal(data.get("price").toString()),
                new BigDecimal(data.get("volume").toString()),
                Instant.now(),
                Boolean.TRUE.equals(data.get("anomaly")));
        l0Bus.publish(new LevelEvent<>(tick, tick.timestamp().toEpochMilli(),
                FsiEventLevels.TICK, null));
    }

    private void dispatchRegimeChange(Map<String, Object> data) {
        var event = new RegimeChanged(
                (String) data.get("instrument"),
                MarketRegime.valueOf((String) data.get("old-regime")),
                MarketRegime.valueOf((String) data.get("new-regime")),
                null);
        regimeChangedEvent.fire(event);
    }
}
