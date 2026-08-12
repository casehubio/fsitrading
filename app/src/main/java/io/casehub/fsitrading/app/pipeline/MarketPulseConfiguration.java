package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.SummarisationRunner;
import io.casehub.blocks.summarisation.WindowPolicy;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.TrendSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MarketPulseConfiguration {

    private static final Logger log = Logger.getLogger(MarketPulseConfiguration.class);

    @Produces @Singleton @Named("l0Bus")
    public EventStreamBus<PriceTick> l0Bus() {
        return new EventStreamBus<>();
    }

    @Produces @Singleton @Named("l1Bus")
    public EventStreamBus<OHLCV> l1Bus() {
        return new EventStreamBus<>();
    }

    @Produces @Singleton @Named("l2Bus")
    public EventStreamBus<TrendSummary> l2Bus() {
        return new EventStreamBus<>();
    }

    @Produces @Singleton @Named("l1Runner")
    public KeyedSummarisationRunner<String, PriceTick, OHLCV> l1Runner(
            @Named("l1Bus") EventStreamBus<OHLCV> l1Bus) {
        return new KeyedSummarisationRunner<>(
                PriceTick::instrument,
                batch -> {
                    if (batch.isEmpty()) return false;
                    long first = batch.get(0).timestamp();
                    long last = batch.get(batch.size() - 1).timestamp();
                    return (last - first) >= 60_000;
                },
                90_000L,
                new FsiTickCompactor(),
                Summariser.ofSync(new FsiOhlcvSummariser()),
                l1Bus,
                FsiEventLevels.BAR_1M,
                failed -> log.warnf("L1 summarisation failed for %d events", failed.size()));
    }

    @Produces @Singleton @Named("l2Runner")
    public SummarisationRunner<OHLCV, TrendSummary> l2Runner(
            @Named("l2Bus") EventStreamBus<TrendSummary> l2Bus) {
        return new SummarisationRunner<>(
                WindowPolicy.ofAge(300_000),
                Summariser.ofSync(new FsiTrendSummariser()),
                l2Bus,
                FsiEventLevels.TREND_5M,
                failed -> log.warnf("L2 summarisation failed for %d events", failed.size()));
    }

    public void wireL0toL2(
            EventStreamBus<PriceTick> l0Bus,
            KeyedSummarisationRunner<String, PriceTick, OHLCV> l1Runner,
            EventStreamBus<OHLCV> l1Bus,
            SummarisationRunner<OHLCV, TrendSummary> l2Runner) {

        l0Bus.subscribe(e -> true, l1Runner::collect);
        l1Bus.subscribe(e -> true, l2Runner::collect);

        log.info("Market Pulse pipeline wired: L0 -> L1 -> L2");
    }
}
