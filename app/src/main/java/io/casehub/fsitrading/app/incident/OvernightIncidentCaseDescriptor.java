package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.IncidentSeverityDescriptor;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.model.MarketEventTypeDescriptor;

import java.util.List;

public class OvernightIncidentCaseDescriptor {

    public List<String> decompositionStepsFor(IncidentSeverity severity) {
        return IncidentSeverityDescriptor.forSeverity(severity).decompositionSteps();
    }

    public String agentNameFor(MarketEventType eventType) {
        return MarketEventTypeDescriptor.forType(eventType).agentName();
    }
}
