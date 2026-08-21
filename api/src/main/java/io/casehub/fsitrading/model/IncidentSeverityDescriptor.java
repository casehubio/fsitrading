package io.casehub.fsitrading.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record IncidentSeverityDescriptor(
        IncidentSeverity severity,
        List<String> decompositionSteps,
        Duration claimDeadline,
        Duration completionDeadline,
        Set<String> candidateGroups) {

    private static final Map<IncidentSeverity, IncidentSeverityDescriptor> REGISTRY = Map.of(
            IncidentSeverity.CRITICAL, new IncidentSeverityDescriptor(
                    IncidentSeverity.CRITICAL,
                    List.of("emergency-halt", "close-positions", "alert-oncall", "verify"),
                    Duration.ofMinutes(2), Duration.ofMinutes(5),
                    Set.of("fsi-oncall")),
            IncidentSeverity.HIGH, new IncidentSeverityDescriptor(
                    IncidentSeverity.HIGH,
                    List.of("reduce-exposure", "hedge", "alert-oncall", "verify"),
                    Duration.ofMinutes(7), Duration.ofMinutes(15),
                    Set.of("fsi-oncall")),
            IncidentSeverity.MEDIUM, new IncidentSeverityDescriptor(
                    IncidentSeverity.MEDIUM,
                    List.of("adjust-limits", "monitor", "verify"),
                    Duration.ofMinutes(30), Duration.ofMinutes(60),
                    Set.of("fsi-oncall"))
    );

    public static IncidentSeverityDescriptor forSeverity(IncidentSeverity severity) {
        return REGISTRY.get(severity);
    }
}
