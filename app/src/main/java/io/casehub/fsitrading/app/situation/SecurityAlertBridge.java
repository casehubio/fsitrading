package io.casehub.fsitrading.app.situation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.fsitrading.model.FsiTradingSecurityEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class SecurityAlertBridge {

    @Inject
    Event<CloudEvent> cloudEventSink;

    @Inject
    ObjectMapper mapper;

    void onSecurityEvent(@ObservesAsync FsiTradingSecurityEvent event) {
        if (event.severity() != FsiTradingSecurityEvent.Severity.CRITICAL) {
            return;
        }
        try {
            byte[] data = mapper.writeValueAsBytes(Map.of(
                    "category", "BREACH",
                    "source", event.source(),
                    "detail", event.detail(),
                    "timestamp", event.timestamp().toString()));

            CloudEvent ce = CloudEventBuilder.v1()
                    .withId(UUID.randomUUID().toString())
                    .withType(FsiTradingEventTypes.SECURITY)
                    .withSource(URI.create("/fsitrading/security"))
                    .withDataContentType("application/json")
                    .withData(data)
                    .build();
            cloudEventSink.fireAsync(ce);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize security alert CloudEvent", e);
        }
    }
}
