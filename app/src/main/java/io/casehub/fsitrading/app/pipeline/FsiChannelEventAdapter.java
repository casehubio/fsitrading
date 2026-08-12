package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.TrendSummary;
import org.jboss.logging.Logger;

import java.util.Map;

public class FsiChannelEventAdapter {

    private static final Logger log = Logger.getLogger(FsiChannelEventAdapter.class);
    private static final String SENTINEL = "##FSI##";

    @FunctionalInterface
    public interface ChannelWriter {
        void write(String channelName, String content, Map<String, String> metadata);
    }

    private final ChannelWriter writer;

    public FsiChannelEventAdapter(ChannelWriter writer) {
        this.writer = writer;
    }

    public void subscribe(EventStreamBus<TrendSummary> l2Bus,
                          EventStreamBus<RegimeAssessment> l3Bus) {
        l2Bus.subscribe(e -> true, event -> writeTrend(event.payload()));
        l3Bus.subscribe(e -> true, event -> writeRegime(event.payload()));
    }

    private void writeTrend(TrendSummary trend) {
        String channelName = "fsi-market-trends-" + trend.instrument();
        var metadata = Map.of(
                "sentinel", SENTINEL,
                "LEVEL", "2",
                "INSTRUMENT", trend.instrument(),
                "EVENT_TYPE", "TREND_SUMMARY");

        String content = String.format("%s: %s momentum=%.4f volatility=%.4f volume=%s [%s - %s]",
                trend.instrument(), trend.direction(), trend.momentum(),
                trend.volatility(), trend.volumeProfile(),
                trend.windowStart(), trend.windowEnd());

        writer.write(channelName, content, metadata);
        log.debugf("Channel write: %s -> %s", channelName, trend.direction());
    }

    private void writeRegime(RegimeAssessment assessment) {
        String channelName = "fsi-market-regime-" + assessment.instrument();
        var metadata = Map.of(
                "sentinel", SENTINEL,
                "LEVEL", "3",
                "INSTRUMENT", assessment.instrument(),
                "EVENT_TYPE", "REGIME_ASSESSMENT");

        String content = String.format("%s: %s (confidence: %.2f) - %s",
                assessment.instrument(), assessment.regime(),
                assessment.confidence(), assessment.rationale());

        writer.write(channelName, content, metadata);
        log.debugf("Channel write: %s -> %s", channelName, assessment.regime());
    }
}
