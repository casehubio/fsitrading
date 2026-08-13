package io.casehub.fsitrading.app.pipeline;

import io.casehub.pages.push.TopicRegistry;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class FsiPushWebSocketTest {

    private TopicRegistry topicRegistry;
    private FsiPushConnectionRegistry connectionRegistry;
    private FsiPushWebSocket endpoint;
    private WebSocketConnection connection;

    @BeforeEach
    void setUp() {
        topicRegistry = new TopicRegistry();
        connectionRegistry = new FsiPushConnectionRegistry();
        endpoint = new FsiPushWebSocket(topicRegistry, connectionRegistry);
        connection = mock(WebSocketConnection.class);
        when(connection.id()).thenReturn("conn-1");
    }

    @Test
    void onOpen_registersConnection() {
        endpoint.onOpen(connection);

        connectionRegistry.send("conn-1", "test");
        verify(connection).sendTextAndAwait("test");
    }

    @Test
    void onClose_removesConnectionAndTopics() {
        endpoint.onOpen(connection);
        endpoint.onMessage("{\"op\":\"listen\",\"id\":\"1\",\"topics\":[\"market:ticks:*\"]}", connection);
        endpoint.onClose(connection);

        connectionRegistry.send("conn-1", "test");
        verify(connection, never()).sendTextAndAwait("test");
        assert topicRegistry.connections("market:ticks:AAPL").isEmpty();
    }

    @Test
    void listen_registersTopicsAndSendsAck() {
        endpoint.onOpen(connection);
        endpoint.onMessage("{\"op\":\"listen\",\"id\":\"req-1\",\"topics\":[\"market:ticks:*\",\"market:regime:*\"]}", connection);

        verify(connection).sendTextAndAwait(argThat(msg ->
                msg.contains("\"op\":\"ack\"") && msg.contains("\"id\":\"req-1\"")));
        assert topicRegistry.connections("market:ticks:AAPL").contains("conn-1");
        assert topicRegistry.connections("market:regime:NVDA").contains("conn-1");
    }

    @Test
    void unlisten_removesTopicsAndSendsAck() {
        endpoint.onOpen(connection);
        endpoint.onMessage("{\"op\":\"listen\",\"id\":\"1\",\"topics\":[\"market:ticks:*\"]}", connection);
        endpoint.onMessage("{\"op\":\"unlisten\",\"id\":\"2\",\"topics\":[\"market:ticks:*\"]}", connection);

        verify(connection).sendTextAndAwait(argThat(msg ->
                msg.contains("\"op\":\"ack\"") && msg.contains("\"id\":\"2\"")));
        assert topicRegistry.connections("market:ticks:AAPL").isEmpty();
    }

    @Test
    void unknownOp_sendsError() {
        endpoint.onOpen(connection);
        endpoint.onMessage("{\"op\":\"subscribe\",\"id\":\"3\",\"dataset\":\"foo\"}", connection);

        verify(connection).sendTextAndAwait(argThat(msg ->
                msg.contains("\"op\":\"error\"") && msg.contains("\"id\":\"3\"")));
    }

    @Test
    void malformedJson_sendsError() {
        endpoint.onOpen(connection);
        endpoint.onMessage("not json", connection);

        verify(connection).sendTextAndAwait(argThat(msg ->
                msg.contains("\"op\":\"error\"")));
    }
}
