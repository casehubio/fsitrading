package io.casehub.fsitrading.model;

public record ExternalIncidentRequest(
        MarketEventType eventType,
        String instrument,
        IncidentSeverity severity,
        String description,
        String source) {
}
