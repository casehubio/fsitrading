package io.casehub.fsitrading.app.situation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.fsitrading.model.FsiTradingSecurityEvent;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityAlertBridgeTest {

    private SecurityAlertBridge bridge;
    @SuppressWarnings("unchecked")
    private final Event<CloudEvent> mockSink = mock(Event.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        bridge = new SecurityAlertBridge();
        bridge.cloudEventSink = mockSink;
        bridge.mapper = mapper;
        when(mockSink.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void criticalEventEmitsCloudEvent() {
        var event = new FsiTradingSecurityEvent(
                FsiTradingSecurityEvent.Severity.CRITICAL,
                "trust-violation", "agent exceeded threshold",
                Instant.parse("2026-09-14T03:00:00Z"));

        bridge.onSecurityEvent(event);

        var captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(mockSink).fireAsync(captor.capture());
        CloudEvent ce = captor.getValue();
        assertThat(ce.getType()).isEqualTo(FsiTradingEventTypes.SECURITY);
        assertThat(ce.getSource().toString()).isEqualTo("/fsitrading/security");
        assertThat(ce.getDataContentType()).isEqualTo("application/json");
        assertThat(ce.getData()).isNotNull();
    }

    @Test
    void warningEventDoesNotEmit() {
        var event = new FsiTradingSecurityEvent(
                FsiTradingSecurityEvent.Severity.WARNING,
                "test", "warning detail", Instant.now());

        bridge.onSecurityEvent(event);

        verify(mockSink, never()).fireAsync(any());
    }

    @Test
    void informationalEventDoesNotEmit() {
        var event = new FsiTradingSecurityEvent(
                FsiTradingSecurityEvent.Severity.INFORMATIONAL,
                "test", "info detail", Instant.now());

        bridge.onSecurityEvent(event);

        verify(mockSink, never()).fireAsync(any());
    }

    @Test
    void cloudEventDataContainsBreachCategory() throws Exception {
        var event = new FsiTradingSecurityEvent(
                FsiTradingSecurityEvent.Severity.CRITICAL,
                "siem-integration", "anomalous access pattern",
                Instant.parse("2026-09-14T03:15:00Z"));

        bridge.onSecurityEvent(event);

        var captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(mockSink).fireAsync(captor.capture());
        byte[] dataBytes = captor.getValue().getData().toBytes();
        var data = mapper.readTree(dataBytes);
        assertThat(data.get("category").asText()).isEqualTo("BREACH");
        assertThat(data.get("source").asText()).isEqualTo("siem-integration");
        assertThat(data.get("detail").asText()).isEqualTo("anomalous access pattern");
        assertThat(data.get("timestamp").asText()).isEqualTo("2026-09-14T03:15:00Z");
    }
}
