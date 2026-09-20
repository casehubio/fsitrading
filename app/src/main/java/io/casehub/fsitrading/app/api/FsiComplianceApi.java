package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.compliance.ComplianceResource;
import io.casehub.fsitrading.app.deliberation.DeliberationResource;
import io.casehub.fsitrading.app.gdpr.GdprErasureResource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.util.UUID;

@McpDomain(value = "fsi/compliance", basePath = "/api/fsi/compliance")
@ApplicationScoped
public class FsiComplianceApi {

    @Inject ComplianceResource complianceResource;
    @Inject DeliberationResource deliberationResource;
    @Inject GdprErasureResource gdprResource;

    @PlatformQuery("Get compliance status")
    @RestPath("/status")
    public Object complianceStatus() {
        return complianceResource.status();
    }

    @PlatformQuery("List deliberations")
    @RestPath("/deliberations")
    public Object listDeliberations(@QueryParam("instrument") String instrument,
                                     @QueryParam("limit") int limit) {
        return deliberationResource.list(instrument, limit);
    }

    @PlatformQuery("Get deliberation by ID")
    @RestPath("/deliberations/{id}")
    public Object getDeliberation(@PathParam UUID id) {
        return deliberationResource.getById(id).getEntity();
    }

    @PlatformMutation("Trigger manual deliberation")
    @RestPath("/deliberations/trigger")
    public Object triggerDeliberation(@QueryParam("instrument") String instrument) {
        return deliberationResource.manualTrigger(instrument).getEntity();
    }

    @PlatformMutation("Request GDPR erasure")
    @RestPath("/gdpr/erase")
    public Object gdprErase(GdprErasureResource.ErasureRequest request) {
        return gdprResource.erase(request).getEntity();
    }
}
