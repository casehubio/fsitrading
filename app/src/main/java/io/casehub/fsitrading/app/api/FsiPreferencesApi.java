package io.casehub.fsitrading.app.api;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@McpDomain(value = "fsi/preferences", basePath = "/api/fsi/preferences")
@ApplicationScoped
public class FsiPreferencesApi {

    @ConfigProperty(name = "casehub.fsitrading.arena.routing.threshold", defaultValue = "0.3")
    double routingThreshold;

    @ConfigProperty(name = "casehub.fsitrading.arena.approval.timeout-hours", defaultValue = "4")
    int approvalTimeoutHours;

    @PlatformQuery("Get trust routing preferences")
    @RestPath("/")
    public Map<String, Object> getPreferences() {
        return Map.of(
                "routingThreshold", routingThreshold,
                "approvalTimeoutHours", approvalTimeoutHours);
    }

    @PlatformMutation("Update trust routing preferences")
    @RestPath("/")
    public Map<String, Object> updatePreferences(Map<String, Object> updates) {
        return Map.of(
                "routingThreshold", routingThreshold,
                "approvalTimeoutHours", approvalTimeoutHours,
                "note", "Runtime updates not yet supported — configure via application.properties");
    }
}
