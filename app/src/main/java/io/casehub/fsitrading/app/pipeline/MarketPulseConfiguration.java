package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.SummarisationRunner;
import io.casehub.blocks.summarisation.WindowPolicy;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.SessionNarrative;
import io.casehub.fsitrading.model.TrendSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

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

    @Produces @Singleton @Named("l3Bus")
    public EventStreamBus<RegimeAssessment> l3Bus() {
        return new EventStreamBus<>();
    }

    @Produces @Singleton @Named("l4Bus")
    public EventStreamBus<SessionNarrative> l4Bus() {
        return new EventStreamBus<>();
    }

    @Produces @Singleton @Named("regimeSummariser")
    public FsiRegimeSummariser regimeSummariser(@Named("llmProvider") Function<String, CompletionStage<String>> llmProvider) {
        return new FsiRegimeSummariser(llmProvider);
    }

    @Produces @Singleton @Named("narrativeSummariser")
    public FsiNarrativeSummariser narrativeSummariser(@Named("llmProvider") Function<String, CompletionStage<String>> llmProvider) {
        return new FsiNarrativeSummariser(llmProvider);
    }

    @Produces @Singleton @Named("l3Runner")
    public SummarisationRunner<TrendSummary, RegimeAssessment> l3Runner(
            @Named("l3Bus") EventStreamBus<RegimeAssessment> l3Bus,
            @Named("regimeSummariser") FsiRegimeSummariser regimeSummariser) {
        return new SummarisationRunner<>(
                WindowPolicy.ofAge(3_600_000),
                regimeSummariser,
                l3Bus,
                FsiEventLevels.REGIME_1H,
                failed -> log.warnf("L3 summarisation failed for %d events", failed.size()));
    }

    @Produces @Singleton @Named("l4Runner")
    public SummarisationRunner<RegimeAssessment, SessionNarrative> l4Runner(
            @Named("l4Bus") EventStreamBus<SessionNarrative> l4Bus,
            @Named("narrativeSummariser") FsiNarrativeSummariser narrativeSummariser) {
        return new SummarisationRunner<>(
                WindowPolicy.ofAge(28_800_000),
                narrativeSummariser,
                l4Bus,
                FsiEventLevels.NARRATIVE,
                failed -> log.warnf("L4 summarisation failed for %d events", failed.size()));
    }

    public void wirePipeline(
            EventStreamBus<PriceTick> l0Bus,
            KeyedSummarisationRunner<String, PriceTick, OHLCV> l1Runner,
            EventStreamBus<OHLCV> l1Bus,
            SummarisationRunner<OHLCV, TrendSummary> l2Runner,
            EventStreamBus<TrendSummary> l2Bus,
            SummarisationRunner<TrendSummary, RegimeAssessment> l3Runner,
            EventStreamBus<RegimeAssessment> l3Bus,
            SummarisationRunner<RegimeAssessment, SessionNarrative> l4Runner) {

        l0Bus.subscribe(e -> true, l1Runner::collect);
        l1Bus.subscribe(e -> true, l2Runner::collect);
        l2Bus.subscribe(e -> true, l3Runner::collect);
        l3Bus.subscribe(e -> true, l4Runner::collect);

        log.info("Market Pulse pipeline wired: L0 -> L1 -> L2 -> L3 -> L4");
    }
}
