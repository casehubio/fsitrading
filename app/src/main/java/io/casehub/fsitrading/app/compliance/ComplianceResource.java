package io.casehub.fsitrading.app.compliance;

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
    public List<ComplianceStatusRecord> status() {
        return complianceService.evaluateAll().stream()
                                .map(ComplianceStatusRecord::from)
                                .toList();
    }
}
