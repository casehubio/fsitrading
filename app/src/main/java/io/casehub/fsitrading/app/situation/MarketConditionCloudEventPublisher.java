package io.casehub.fsitrading.app.situation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class MarketConditionCloudEventPublisher {

    static final String CLOUD_EVENT_TYPE = "io.casehub.fsitrading.market.assessment";
    static final URI SOURCE = URI.create("/fsitrading/market-pulse");

    @Inject
    Event<CloudEvent> cloudEventSink;

    @Inject
    ObjectMapper mapper;

    public void onRegimeAssessment(RegimeAssessment assessment) {
        try {
            byte[] data = mapper.writeValueAsBytes(Map.of(
                    "instrument", assessment.instrument(),
                    "regime", assessment.regime().name(),
                    "confidence", assessment.confidence(),
                    "rationale", assessment.rationale() != null ? assessment.rationale() : "",
                    "timestamp", assessment.timestamp().toString()));

            CloudEvent ce = CloudEventBuilder.v1()
                    .withId(UUID.randomUUID().toString())
                    .withType(CLOUD_EVENT_TYPE)
                    .withSource(SOURCE)
                    .withDataContentType("application/json")
                    .withData(data)
                    .build();
            cloudEventSink.fireAsync(ce);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize market assessment CloudEvent", e);
        }
    }

}
