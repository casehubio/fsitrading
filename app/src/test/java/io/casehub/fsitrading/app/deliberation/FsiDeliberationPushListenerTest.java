package io.casehub.fsitrading.app.deliberation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiDeliberationPushListenerTest {

    private final List<BroadcastCapture> broadcasts = new ArrayList<>();
    private FsiDeliberationPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new FsiDeliberationPushListener(
                (topic, event) -> broadcasts.add(new BroadcastCapture(topic, event)));
    }

    @Test
    void started_broadcastsToBothTopics() {
        var channelId = UUID.randomUUID();
        var event = new DeliberationStartedEvent(
                UUID.randomUUID(), channelId, "AAPL",
                "REGIME_CHANGED", "agent-1,agent-2", Instant.now());

        listener.onStarted(event);

        assertEquals(2, broadcasts.size());
        assertEquals("deliberation:active", broadcasts.get(0).topic);
        assertEquals("deliberation:" + channelId, broadcasts.get(1).topic);
        assertInstanceOf(DeliberationPushPayload.Started.class, broadcasts.get(0).event);
        assertInstanceOf(DeliberationPushPayload.Started.class, broadcasts.get(1).event);
    }

    @Test
    void completed_broadcastsToBothTopics() {
        var channelId = UUID.randomUUID();
        var event = new DeliberationCompletedEvent(
                UUID.randomUUID(), channelId, "MSFT",
                "CONSENSUS", 0.95, 5, 1, 2, 3,
                UUID.randomUUID(), UUID.randomUUID(), "EXECUTE", Instant.now());

        listener.onCompleted(event);

        assertEquals(2, broadcasts.size());
        assertEquals("deliberation:active", broadcasts.get(0).topic);
        assertEquals("deliberation:" + channelId, broadcasts.get(1).topic);
        assertInstanceOf(DeliberationPushPayload.Completed.class, broadcasts.get(0).event);
    }

    @Test
    void failed_broadcastsToBothTopics() {
        var channelId = UUID.randomUUID();
        var event = new DeliberationFailedEvent(
                UUID.randomUUID(), channelId, "GOOGL",
                "Wall-clock timeout after 900s", Instant.now());

        listener.onFailed(event);

        assertEquals(2, broadcasts.size());
        assertEquals("deliberation:active", broadcasts.get(0).topic);
        assertEquals("deliberation:" + channelId, broadcasts.get(1).topic);
        assertInstanceOf(DeliberationPushPayload.Failed.class, broadcasts.get(0).event);
    }

    @Test
    void started_payloadCarriesAllFields() {
        var deliberationId = UUID.randomUUID();
        var channelId = UUID.randomUUID();
        var now = Instant.now();
        var event = new DeliberationStartedEvent(
                deliberationId, channelId, "AAPL",
                "TREND_REVERSAL", "agent-1,agent-2", now);

        listener.onStarted(event);

        var payload = (DeliberationPushPayload.Started) broadcasts.get(0).event;
        assertEquals("DELIBERATION_STARTED", payload.type());
        assertEquals(deliberationId, payload.deliberationId());
        assertEquals(channelId, payload.channelId());
        assertEquals("AAPL", payload.instrument());
        assertEquals("TREND_REVERSAL", payload.triggerType());
        assertEquals(now, payload.startedAt());
    }

    record BroadcastCapture(String topic, Object event) {}
}
