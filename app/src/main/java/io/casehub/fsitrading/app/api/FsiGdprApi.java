package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.gdpr.FsiErasureResult;
import io.casehub.fsitrading.app.gdpr.FsiGdprErasureService;
import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpDomain(value = "fsi/gdpr", basePath = "/api/fsi/gdpr")
@ApplicationScoped
public class FsiGdprApi {

    @Inject
    FsiGdprErasureService erasureService;

    @PlatformMutation("Erase personal data for a subject (GDPR Art.17)")
    @RestPath("/erase")
    public FsiErasureResult erase(ErasureRequest request) {
        if (request == null || request.subjectId() == null || request.subjectId().isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        return erasureService.erase(
                request.subjectId(),
                TenancyConstants.DEFAULT_TENANT_ID,
                request.reason());
    }

    public record ErasureRequest(String subjectId, ErasureReason reason) {}
}
