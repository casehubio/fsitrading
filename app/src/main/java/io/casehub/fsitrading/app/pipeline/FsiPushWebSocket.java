package io.casehub.fsitrading.app.pipeline;

import io.casehub.pages.push.PushMessage;
import io.casehub.pages.push.PushRequest;
import io.casehub.pages.push.TopicRegistry;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

@WebSocket(path = "/ws/push")
public class FsiPushWebSocket {

    private final TopicRegistry topicRegistry;
    private final FsiPushConnectionRegistry connectionRegistry;

    @Inject
    public FsiPushWebSocket(TopicRegistry topicRegistry, FsiPushConnectionRegistry connectionRegistry) {
        this.topicRegistry = topicRegistry;
        this.connectionRegistry = connectionRegistry;
    }

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        connectionRegistry.register(connection.id(), connection);
    }

    @OnTextMessage
    void onMessage(String message, WebSocketConnection connection) {
        PushRequest request;
        try {
            request = PushRequest.parse(message);
        } catch (Exception e) {
            connection.sendTextAndAwait(PushMessage.error("unknown", "malformed request: " + e.getMessage()));
            return;
        }

        switch (request) {
            case PushRequest.Listen listen -> {
                topicRegistry.listen(connection.id(), listen.topics());
                connection.sendTextAndAwait(PushMessage.ack(listen.id(), listen.topics()));
            }
            case PushRequest.Unlisten unlisten -> {
                topicRegistry.unlisten(connection.id(), unlisten.topics());
                connection.sendTextAndAwait(PushMessage.ack(unlisten.id()));
            }
            default -> connection.sendTextAndAwait(
                    PushMessage.error(request.id(), "unsupported op: " + request.op()));
        }
    }

    @OnClose
    void onClose(WebSocketConnection connection) {
        topicRegistry.removeConnection(connection.id());
        connectionRegistry.remove(connection.id());
    }
}
