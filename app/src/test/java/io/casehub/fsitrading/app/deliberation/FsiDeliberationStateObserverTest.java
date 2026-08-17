package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.agentic.model.DriverEvent;
import io.casehub.blocks.agentic.model.EventSource;
import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.blocks.conversation.ConvergenceSignal;
import io.casehub.blocks.conversation.ConvergenceState;
import io.casehub.blocks.conversation.EpistemicRules;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiDeliberationStateObserverTest {

    private static final String CHANNEL = "fsi-deliberation-AAPL-20260816";
    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final int RECENT_WINDOW = 5;

    private FsiConversationProjection projection;
    private FsiDeliberationStateObserver observer;
    private final List<BroadcastCapture> broadcasts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        projection = new FsiConversationProjection();
        broadcasts.clear();
        observer = new FsiDeliberationStateObserver(projection, CHANNEL, CHANNEL_ID,
                EpistemicRules.explicitAcknowledgement(2),
                (state, commonGround, context) -> new ConvergenceSignal(
                        ConvergenceState.PROGRESSING, 0.3, "debate active"),
                RECENT_WINDOW,
                (topic, event) -> broadcasts.add(new BroadcastCapture(topic, event)));
    }

    private FsiDeliberationStateObserver observerWithoutBroadcaster() {
        return new FsiDeliberationStateObserver(projection, CHANNEL, CHANNEL_ID,
                EpistemicRules.explicitAcknowledgement(2),
                (state, commonGround, context) -> new ConvergenceSignal(
                        ConvergenceState.PROGRESSING, 0.0, "test"),
                RECENT_WINDOW, null);
    }

    @Test
    void initialStateIsIdentity() {
        var state = observer.currentState();
        assertNotNull(state);
        assertTrue(state.points().isEmpty());
    }

    @Test
    void onMessageAppliesProjectionAndUpdatesState() {
        var pointId = UUID.randomUUID().toString();
        observer.onMessage(raiseEvent(pointId, "momentum-strategy", "AAPL thesis"));

        var state = observer.currentState();
        assertEquals(1, state.points().size());
        assertNotNull(state.points().get(pointId));
    }

    @Test
    void subscribeReceivesDriverEventsOnMessage() {
        List<DriverEvent> received = new ArrayList<>();
        observer.subscribe(received::add);

        observer.onMessage(raiseEvent(UUID.randomUUID().toString(), "momentum-strategy", "thesis"));

        assertEquals(1, received.size());
        assertEquals("message", received.get(0).source());
    }

    @Test
    void cancellationStopsEventDelivery() {
        List<DriverEvent> received = new ArrayList<>();
        var cancellation = observer.subscribe(received::add);

        observer.onMessage(raiseEvent(UUID.randomUUID().toString(), "momentum-strategy", "thesis 1"));
        assertEquals(1, received.size());

        cancellation.cancel();
        observer.onMessage(raiseEvent(UUID.randomUUID().toString(), "momentum-strategy", "thesis 2"));
        assertEquals(1, received.size());
    }

    @Test
    void channelsReturnsConfiguredChannel() {
        assertEquals(Set.of(CHANNEL), observer.channels());
    }

    @Test
    void scopeIsLocal() {
        assertEquals(MessageObserver.Scope.LOCAL, observer.scope());
    }

    @Test
    void stateIsImmutablePerMessage() {
        var pointId1 = UUID.randomUUID().toString();
        var pointId2 = UUID.randomUUID().toString();

        observer.onMessage(raiseEvent(pointId1, "momentum-strategy", "thesis 1"));
        var stateAfterFirst = observer.currentState();

        observer.onMessage(raiseEvent(pointId2, "mean-reversion-strategy", "thesis 2"));
        var stateAfterSecond = observer.currentState();

        assertEquals(1, stateAfterFirst.points().size());
        assertEquals(2, stateAfterSecond.points().size());
        assertNotSame(stateAfterFirst, stateAfterSecond);
    }

    @Test
    void doubleSubscribeReplacesFirstSink() {
        List<DriverEvent> firstSink = new ArrayList<>();
        List<DriverEvent> secondSink = new ArrayList<>();

        observer.subscribe(firstSink::add);
        observer.subscribe(secondSink::add);

        observer.onMessage(raiseEvent(UUID.randomUUID().toString(), "momentum-strategy", "thesis"));

        assertTrue(firstSink.isEmpty(), "first sink should no longer receive events");
        assertEquals(1, secondSink.size(), "second sink should receive events");
    }

    @Test
    void onMessage_broadcastsConvergenceUpdate() {
        var pointId = UUID.randomUUID().toString();
        observer.onMessage(raiseEvent(pointId, "momentum-strategy", "BUY 100 AAPL"));

        assertEquals(1, broadcasts.size());
        assertEquals("deliberation:" + CHANNEL_ID, broadcasts.get(0).topic);
        var payload = (DeliberationPushPayload.ConvergenceUpdate) broadcasts.get(0).event;
        assertEquals("CONVERGENCE_UPDATE", payload.type());
        assertEquals(CHANNEL_ID, payload.channelId());
        assertEquals("PROGRESSING", payload.convergenceState());
        assertEquals(0.3, payload.confidence(), 0.001);
        assertEquals(1, payload.dispatchCount());
    }

    @Test
    void onMessage_incrementsDispatchCountAcrossMessages() {
        observer.onMessage(raiseEvent(UUID.randomUUID().toString(), "momentum-strategy", "thesis 1"));
        observer.onMessage(raiseEvent(UUID.randomUUID().toString(), "mean-reversion-strategy", "thesis 2"));

        assertEquals(2, broadcasts.size());
        var first = (DeliberationPushPayload.ConvergenceUpdate) broadcasts.get(0).event;
        var second = (DeliberationPushPayload.ConvergenceUpdate) broadcasts.get(1).event;
        assertEquals(1, first.dispatchCount());
        assertEquals(2, second.dispatchCount());
    }

    @Test
    void nullBroadcaster_doesNotThrow() {
        var obs = observerWithoutBroadcaster();
        assertDoesNotThrow(() ->
                obs.onMessage(raiseEvent(UUID.randomUUID().toString(), "momentum-strategy", "thesis")));
        assertEquals(1, obs.currentState().points().size());
    }

    private MessageReceivedEvent raiseEvent(String correlationId, String role, String body) {
        var content = ChannelMessageMeta.encode("##FSI##",
                Map.of("entryType", "RAISE", "role", role, "round", "1"),
                body);
        return new MessageReceivedEvent(1L, CHANNEL, CHANNEL_ID, null,
                MessageType.COMMAND, "rule:momentum@v1", null, null,
                correlationId, Instant.now(), content, "debate");
    }

    record BroadcastCapture(String topic, Object event) {}
}
