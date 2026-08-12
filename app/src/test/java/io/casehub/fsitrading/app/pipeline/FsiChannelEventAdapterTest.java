package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FsiChannelEventAdapterTest {

    private EventStreamBus<TrendSummary> l2Bus;
    private EventStreamBus<RegimeAssessment> l3Bus;
    private FsiChannelEventAdapter adapter;
    private final List<ChannelWriteCapture> writes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        l2Bus = new EventStreamBus<>();
        l3Bus = new EventStreamBus<>();
        adapter = new FsiChannelEventAdapter(
                (channelName, content, metadata) -> writes.add(
                        new ChannelWriteCapture(channelName, content, metadata)));
        adapter.subscribe(l2Bus, l3Bus);
    }

    @Test
    void trendSummary_writesToInstrumentChannel() {
        var now = Instant.now();
        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("AAPL", TrendDirection.UP, 0.02, 0.01, "FLAT", now, now),
                now.toEpochMilli(), FsiEventLevels.TREND_5M));

        assertEquals(1, writes.size());
        assertEquals("fsi-market-trends-AAPL", writes.get(0).channelName);
        assertEquals("##FSI##", writes.get(0).metadata.get("sentinel"));
        assertEquals("2", writes.get(0).metadata.get("LEVEL"));
        assertEquals("AAPL", writes.get(0).metadata.get("INSTRUMENT"));
        assertEquals("TREND_SUMMARY", writes.get(0).metadata.get("EVENT_TYPE"));
    }

    @Test
    void regimeAssessment_writesToInstrumentChannel() {
        var now = Instant.now();
        l3Bus.publish(new LevelEvent<>(
                new RegimeAssessment("MSFT", MarketRegime.VOLATILE, 0.90, "volatile", now),
                now.toEpochMilli(), FsiEventLevels.REGIME_1H));

        assertEquals(1, writes.size());
        assertEquals("fsi-market-regime-MSFT", writes.get(0).channelName);
        assertEquals("3", writes.get(0).metadata.get("LEVEL"));
        assertEquals("REGIME_ASSESSMENT", writes.get(0).metadata.get("EVENT_TYPE"));
    }

    record ChannelWriteCapture(String channelName, String content, Map<String, String> metadata) {}
}
