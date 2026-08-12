package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.RegimeChanged;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendReversalDetected;
import io.casehub.fsitrading.model.TrendSummary;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FsiMarketEventDetector {

    private static final Logger log = Logger.getLogger(FsiMarketEventDetector.class);

    private final ConcurrentHashMap<String, TrendDirection> previousDirections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MarketRegime> previousRegimes = new ConcurrentHashMap<>();

    private final Consumer<TrendReversalDetected> reversalConsumer;
    private final Consumer<RegimeChanged> regimeChangeConsumer;

    public FsiMarketEventDetector(Consumer<TrendReversalDetected> reversalConsumer,
                                   Consumer<RegimeChanged> regimeChangeConsumer) {
        this.reversalConsumer = reversalConsumer;
        this.regimeChangeConsumer = regimeChangeConsumer;
    }

    public void subscribe(EventStreamBus<TrendSummary> l2Bus,
                          EventStreamBus<RegimeAssessment> l3Bus) {
        l2Bus.subscribe(e -> true, event -> detectTrendReversal(event.payload()));
        l3Bus.subscribe(e -> true, event -> detectRegimeChange(event.payload()));
    }

    private void detectTrendReversal(TrendSummary trend) {
        var previous = previousDirections.put(trend.instrument(), trend.direction());
        if (previous != null && previous != trend.direction()) {
            log.infof("Trend reversal detected: %s %s -> %s",
                    trend.instrument(), previous, trend.direction());
            reversalConsumer.accept(new TrendReversalDetected(
                    trend.instrument(), previous, trend.direction(), trend));
        }
    }

    private void detectRegimeChange(RegimeAssessment assessment) {
        var previous = previousRegimes.put(assessment.instrument(), assessment.regime());
        if (previous != null && previous != assessment.regime()) {
            log.infof("Regime change detected: %s %s -> %s",
                    assessment.instrument(), previous, assessment.regime());
            regimeChangeConsumer.accept(new RegimeChanged(
                    assessment.instrument(), previous, assessment.regime(), assessment));
        }
    }
}
