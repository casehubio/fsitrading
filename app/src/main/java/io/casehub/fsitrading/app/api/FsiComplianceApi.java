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

import java.util.UUID;

@McpDomain(value = "fsi/compliance", basePath = "/api/fsi/compliance")
@ApplicationScoped
public class FsiComplianceApi {

    @Inject ComplianceResource complianceResource;
    @Inject DeliberationResource deliberationResource;
    @Inject GdprErasureResource gdprResource;

    @PlatformQuery("Get compliance status")
    @RestPath("/status")
    public List<ComplianceStatusRecord> complianceStatus() {
        return complianceResource.status();
    }

    @PlatformQuery("List deliberations")
    @RestPath("/deliberations")
    public List<DeliberationRecord> listDeliberations(String instrument, Integer limit) {
        return deliberationResource.list(instrument, null, null);
    }

    @PlatformQuery("Get a deliberation by ID")
    @RestPath("/deliberations/{id}")
    public DeliberationRecord getDeliberation(@PathParam UUID id) {
        return (DeliberationRecord) deliberationResource.getById(id).getEntity();
    }

    @PlatformMutation("Trigger a deliberation for an instrument")
    @RestPath("/deliberations/trigger")
    public UUID triggerDeliberation(String instrument) {
        return (UUID) deliberationResource.manualTrigger(instrument).getEntity();
    }

    @PlatformMutation("Erase personal data (GDPR)")
    @RestPath("/gdpr/erase")
    public FsiErasureResult gdprErase(ErasureRequest request) {
        return (FsiErasureResult) gdprResource.erase(request).getEntity();
    }
}
