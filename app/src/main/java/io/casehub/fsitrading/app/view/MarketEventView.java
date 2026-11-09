package io.casehub.fsitrading.app.view;

import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.model.MarketEventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketEventView(UUID id, String instrument, MarketEventType eventType,
                              BigDecimal price, BigDecimal volume,
                              String data, Instant occurredAt) {

    public static MarketEventView toView(MarketEventEntity e) {
        return new MarketEventView(e.getId(), e.getInstrument(), e.getEventType(),
                e.getPrice(), e.getVolume(),
                e.getData(), e.getOccurredAt());
    }
}
