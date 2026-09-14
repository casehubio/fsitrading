package io.casehub.fsitrading.app.situation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarketConditionCloudEventPublisherTest {

    private MarketConditionCloudEventPublisher publisher;
    @SuppressWarnings("unchecked")
    private final Event<CloudEvent> mockSink = mock(Event.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        publisher = new MarketConditionCloudEventPublisher();
        publisher.cloudEventSink = mockSink;
        publisher.mapper = mapper;
        when(mockSink.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void publishesRegimeAssessmentAsCloudEvent() {
        var assessment = new RegimeAssessment(
                "BTCUSD", MarketRegime.VOLATILE, 0.85,
                "price spike detected", Instant.parse("2026-09-14T10:00:00Z"));

        publisher.onRegimeAssessment(assessment);

        var captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(mockSink).fireAsync(captor.capture());
        CloudEvent ce = captor.getValue();
        assertThat(ce.getType()).isEqualTo(MarketConditionCloudEventPublisher.CLOUD_EVENT_TYPE);
        assertThat(ce.getSource()).isEqualTo(MarketConditionCloudEventPublisher.SOURCE);
        assertThat(ce.getDataContentType()).isEqualTo("application/json");
    }

    @Test
    void cloudEventDataContainsAssessmentFields() throws Exception {
        var assessment = new RegimeAssessment(
                "ETHUSD", MarketRegime.QUIET, 0.95,
                "normal trading", Instant.parse("2026-09-14T11:00:00Z"));

        publisher.onRegimeAssessment(assessment);

        var captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(mockSink).fireAsync(captor.capture());
        byte[] dataBytes = captor.getValue().getData().toBytes();
        var data = mapper.readTree(dataBytes);
        assertThat(data.get("instrument").asText()).isEqualTo("ETHUSD");
        assertThat(data.get("regime").asText()).isEqualTo("QUIET");
        assertThat(data.get("confidence").asDouble()).isEqualTo(0.95);
        assertThat(data.get("rationale").asText()).isEqualTo("normal trading");
        assertThat(data.get("timestamp").asText()).isEqualTo("2026-09-14T11:00:00Z");
    }

    @Test
    void nullRationaleSerializesAsEmptyString() throws Exception {
        var assessment = new RegimeAssessment(
                "BTCUSD", MarketRegime.VOLATILE, 0.7,
                null, Instant.parse("2026-09-14T12:00:00Z"));

        publisher.onRegimeAssessment(assessment);

        var captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(mockSink).fireAsync(captor.capture());
        byte[] dataBytes = captor.getValue().getData().toBytes();
        var data = mapper.readTree(dataBytes);
        assertThat(data.get("rationale").asText()).isEmpty();
    }
}
