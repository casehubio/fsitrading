package io.casehub.fsitrading.app.compliance;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@io.casehub.platform.api.mcp.HandWrittenEndpoint("pending @McpDomain migration")
@Path("/api/compliance")
public class ComplianceResource  {

    @Inject FsiComplianceService complianceService;

    @GET
    @Path("/status")
    public List<ComplianceStatusRecord> status() {
        return complianceService.evaluateAll().stream()
                                .map(ComplianceStatusRecord::from)
                                .toList();
    }
}
