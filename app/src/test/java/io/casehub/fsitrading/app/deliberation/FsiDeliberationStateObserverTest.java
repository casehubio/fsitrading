package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.agentic.model.DriverEvent;
import io.casehub.blocks.agentic.model.EventSource;
import io.casehub.blocks.channel.ChannelMessageMeta;
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

    private FsiConversationProjection projection;
    private FsiDeliberationStateObserver observer;

    @BeforeEach
    void setUp() {
        projection = new FsiConversationProjection();
        observer = new FsiDeliberationStateObserver(projection, CHANNEL);
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

    private MessageReceivedEvent raiseEvent(String correlationId, String role, String body) {
        var content = ChannelMessageMeta.encode("##FSI##",
                Map.of("entryType", "RAISE", "role", role, "round", "1"),
                body);
        return new MessageReceivedEvent(1L, CHANNEL, CHANNEL_ID, null,
                MessageType.COMMAND, "rule:momentum@v1", null, null,
                correlationId, Instant.now(), content, "debate");
    }
}
