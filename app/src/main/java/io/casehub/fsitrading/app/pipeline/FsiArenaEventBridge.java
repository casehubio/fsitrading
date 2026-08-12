package io.casehub.fsitrading.app.pipeline;

import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.RegimeChanged;
import io.casehub.fsitrading.model.TrendReversalDetected;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Consumer;

public class FsiArenaEventBridge {

    private static final Logger log = Logger.getLogger(FsiArenaEventBridge.class);

    private final Consumer<MarketSignal> arenaTrigger;

    public FsiArenaEventBridge(Consumer<MarketSignal> arenaTrigger) {
        this.arenaTrigger = arenaTrigger;
    }

    public void onTrendReversal(TrendReversalDetected event) {
        log.infof("Bridging trend reversal to arena: %s %s -> %s",
                event.instrument(), event.oldDirection(), event.newDirection());
        var signal = new MarketSignal(
                event.instrument(),
                "TREND_REVERSAL",
                event.trendSummary() != null ? BigDecimal.valueOf(event.trendSummary().momentum()) : BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.now());
        arenaTrigger.accept(signal);
    }

    public void onRegimeChanged(RegimeChanged event) {
        log.infof("Bridging regime change to arena: %s %s -> %s",
                event.instrument(), event.oldRegime(), event.newRegime());
        var signal = new MarketSignal(
                event.instrument(),
                "REGIME_CHANGE",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.now());
        arenaTrigger.accept(signal);
    }
}
