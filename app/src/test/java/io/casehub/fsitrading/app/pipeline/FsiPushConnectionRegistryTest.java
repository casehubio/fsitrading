package io.casehub.fsitrading.app.pipeline;

import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FsiPushConnectionRegistryTest {

    private FsiPushConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new FsiPushConnectionRegistry();
    }

    @Test
    void sendToRegisteredConnection() {
        var conn = mock(WebSocketConnection.class);
        registry.register("conn-1", conn);

        registry.send("conn-1", "{\"op\":\"event\"}");

        verify(conn).sendTextAndAwait("{\"op\":\"event\"}");
    }

    @Test
    void sendToUnregisteredConnectionIsNoOp() {
        registry.send("unknown", "{\"op\":\"event\"}");
        // no exception
    }

    @Test
    void removeUnregistersConnection() {
        var conn = mock(WebSocketConnection.class);
        registry.register("conn-1", conn);
        registry.remove("conn-1");

        registry.send("conn-1", "{\"op\":\"event\"}");

        verifyNoInteractions(conn);
    }

    @Test
    void registerOverwritesPreviousConnection() {
        var conn1 = mock(WebSocketConnection.class);
        var conn2 = mock(WebSocketConnection.class);
        registry.register("conn-1", conn1);
        registry.register("conn-1", conn2);

        registry.send("conn-1", "msg");

        verifyNoInteractions(conn1);
        verify(conn2).sendTextAndAwait("msg");
    }

    @Test
    void sendSwallowsExceptions() {
        var conn = mock(WebSocketConnection.class);
        doThrow(new RuntimeException("closed")).when(conn).sendTextAndAwait(anyString());
        registry.register("conn-1", conn);

        assertDoesNotThrow(() -> registry.send("conn-1", "msg"));
    }
}
