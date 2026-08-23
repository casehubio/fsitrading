package io.casehub.fsitrading.model;

import java.util.List;

public record IncidentSummary(
        long totalActive,
        String slaStatus,
        List<SeverityCount> bySeverity
) {
    public record SeverityCount(String severity, long count) {}
}
