package io.casehub.fsitrading.model;

import java.time.Instant;

public record IncidentTimelineRecord(
        String milestone,
        Instant timestamp,
        String description) {
}
