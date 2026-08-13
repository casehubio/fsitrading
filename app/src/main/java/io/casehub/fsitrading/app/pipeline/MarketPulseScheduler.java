package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.SummarisationRunner;
import io.casehub.fsitrading.app.service.SyntheticMarketDataProvider;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.SessionNarrative;
import io.casehub.fsitrading.model.TrendSummary;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class MarketPulseScheduler {

    private static final Logger log = Logger.getLogger(MarketPulseScheduler.class);

    @Inject @Named("l0Bus")
    EventStreamBus<PriceTick> l0Bus;

    @Inject @Named("l1Runner")
    KeyedSummarisationRunner<String, PriceTick, OHLCV> l1Runner;

    @Inject @Named("l2Runner")
    SummarisationRunner<OHLCV, TrendSummary> l2Runner;

    @Inject @Named("l2Bus")
    EventStreamBus<TrendSummary> l2Bus;

    @Inject @Named("l3Runner")
    SummarisationRunner<TrendSummary, RegimeAssessment> l3Runner;

    @Inject @Named("l3Bus")
    EventStreamBus<RegimeAssessment> l3Bus;

    @Inject @Named("l4Runner")
    SummarisationRunner<RegimeAssessment, SessionNarrative> l4Runner;

    @Inject
    SyntheticMarketDataProvider tickProvider;

    @Inject
    MarketPulseConfiguration configuration;

    @Inject @Named("l1Bus")
    EventStreamBus<OHLCV> l1Bus;

    @Inject @Named("l4Bus")
    EventStreamBus<SessionNarrative> l4Bus;

    @Inject
    FsiMarketPushService pushService;

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile boolean wired = false;

    void onStart(@Observes StartupEvent event) {
        configuration.wirePipeline(l0Bus, l1Runner, l1Bus, l2Runner, l2Bus, l3Runner, l3Bus, l4Runner);
        pushService.subscribe(l0Bus, l1Bus, l2Bus, l3Bus, l4Bus);
        wired = true;
        log.info("Market Pulse scheduler started");
    }

    @Scheduled(every = "${fsi.market.tick-interval:500ms}", identity = "market-pulse-tick")
    void generateTick() {
        if (paused.get() || !wired) return;

        PriceTick tick = tickProvider.generateTick();
        l0Bus.publish(new LevelEvent<>(tick, tick.timestamp().toEpochMilli(), FsiEventLevels.TICK));
    }

    @Scheduled(every = "0.1s", identity = "market-pulse-driver")
    void driveRunners() {
        if (!wired) return;
        long now = System.currentTimeMillis();
        l1Runner.tick(now);
        l2Runner.tick(now);
        l3Runner.tick(now);
        l4Runner.tick(now);
    }

    public void pause() {
        paused.set(true);
        log.info("Market Pulse scheduler paused");
    }

    public void resume() {
        paused.set(false);
        log.info("Market Pulse scheduler resumed");
    }

    public boolean isPaused() {
        return paused.get();
    }
}
