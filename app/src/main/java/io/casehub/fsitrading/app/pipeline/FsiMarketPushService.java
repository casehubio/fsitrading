package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.SessionNarrative;
import io.casehub.fsitrading.model.TrendSummary;
import org.jboss.logging.Logger;

import java.util.function.BiConsumer;

public class FsiMarketPushService {

    private static final Logger log = Logger.getLogger(FsiMarketPushService.class);

    @FunctionalInterface
    public interface PushBroadcaster {
        void broadcast(String topic, Object event);
    }

    private final PushBroadcaster broadcaster;

    public FsiMarketPushService(PushBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    public void subscribe(
            EventStreamBus<PriceTick> l0Bus,
            EventStreamBus<OHLCV> l1Bus,
            EventStreamBus<TrendSummary> l2Bus,
            EventStreamBus<RegimeAssessment> l3Bus,
            EventStreamBus<SessionNarrative> l4Bus) {

        l0Bus.subscribe(e -> true, event -> {
            var tick = event.payload();
            broadcaster.broadcast("market:ticks:" + tick.instrument(), tick);
        });

        l1Bus.subscribe(e -> true, event -> {
            var bar = event.payload();
            broadcaster.broadcast("market:bars:" + bar.instrument(), bar);
        });

        l2Bus.subscribe(e -> true, event -> {
            var trend = event.payload();
            broadcaster.broadcast("market:trends:" + trend.instrument(), trend);
        });

        l3Bus.subscribe(e -> true, event -> {
            var regime = event.payload();
            broadcaster.broadcast("market:regime:" + regime.instrument(), regime);
        });

        l4Bus.subscribe(e -> true, event -> {
            var narrative = event.payload();
            broadcaster.broadcast("market:narrative", narrative);
        });

        log.info("Market Pulse push service subscribed to all level buses");
    }
}
