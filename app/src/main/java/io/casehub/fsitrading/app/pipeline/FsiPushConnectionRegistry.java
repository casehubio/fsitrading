package io.casehub.fsitrading.app.pipeline;

import io.casehub.pages.push.SessionSender;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class FsiPushConnectionRegistry implements SessionSender {

    private final ConcurrentHashMap<String, WebSocketConnection> connections = new ConcurrentHashMap<>();

    public void register(String id, WebSocketConnection connection) {
        connections.put(id, connection);
    }

    public void remove(String id) {
        connections.remove(id);
    }

    @Override
    public void send(String connectionId, String message) {
        var conn = connections.get(connectionId);
        if (conn != null) {
            try {
                conn.sendTextAndAwait(message);
            } catch (Exception ignored) {
            }
        }
    }
}
