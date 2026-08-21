package io.casehub.fsitrading.app.incident.agent;

import java.util.Map;

@FunctionalInterface
public interface IncidentResponseAgent {

    Map<String, Object> execute(Map<String, Object> input);
}
