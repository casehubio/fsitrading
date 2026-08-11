package io.casehub.fsitrading.app.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@Path("/api/preferences/trust-routing")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PreferencesResource {

    @ConfigProperty(name = "casehub.fsitrading.arena.routing.threshold", defaultValue = "0.3")
    double routingThreshold;

    @ConfigProperty(name = "casehub.fsitrading.arena.approval.timeout-hours", defaultValue = "4")
    int approvalTimeoutHours;

    @GET
    public Map<String, Object> getPreferences() {
        return Map.of(
                "routingThreshold", routingThreshold,
                "approvalTimeoutHours", approvalTimeoutHours);
    }

    @PUT
    public Map<String, Object> updatePreferences(Map<String, Object> updates) {
        return Map.of(
                "routingThreshold", routingThreshold,
                "approvalTimeoutHours", approvalTimeoutHours,
                "note", "Runtime updates not yet supported — configure via application.properties");
    }
}
