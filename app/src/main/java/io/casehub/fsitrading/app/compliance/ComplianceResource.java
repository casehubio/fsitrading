package io.casehub.fsitrading.app.compliance;

import io.casehub.api.spi.routing.TrustRoutingRequirement;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/compliance")
@Produces(MediaType.APPLICATION_JSON)
public class ComplianceResource {

    @Inject FsiComplianceService complianceService;

    @GET
    @Path("/status")
    public List<TrustRoutingRequirement> status() {
        return complianceService.evaluateAll();
    }
}
