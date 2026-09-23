package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.compliance.ComplianceStatusRecord;
import io.casehub.fsitrading.app.compliance.FsiComplianceService;
import io.casehub.fsitrading.app.deliberation.DeliberationRecord;
import io.casehub.fsitrading.app.deliberation.DeliberationRecordRepository;
import io.casehub.fsitrading.app.deliberation.FsiDeliberationOrchestrator;
import io.casehub.fsitrading.app.gdpr.FsiErasureResult;
import io.casehub.fsitrading.app.gdpr.FsiGdprErasureService;
import io.casehub.fsitrading.app.gdpr.GdprErasureResource;
import io.casehub.fsitrading.app.ledger.TradingLedgerService;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.QueryParam;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "fsi/compliance", basePath = "/api/fsi/compliance")
@ApplicationScoped
public class FsiComplianceApi {

    @Inject FsiComplianceService complianceService;
    @Inject DeliberationRecordRepository deliberationRepository;
    @Inject FsiDeliberationOrchestrator deliberationOrchestrator;
    @Inject FsiGdprErasureService erasureService;
    @Inject TradingLedgerService tradingLedgerService;

    @PlatformQuery("Get compliance status")
    @RestPath("/status")
    public List<ComplianceStatusRecord> complianceStatus() {
        return complianceService.evaluateAll().stream()
                .map(ComplianceStatusRecord::from)
                .toList();
    }

    @PlatformQuery("List deliberations")
    @RestPath("/deliberations")
    public List<DeliberationRecord> listDeliberations(@QueryParam("instrument") String instrument,
                                                       @QueryParam("limit") int limit) {
        return deliberationRepository.findAll();
    }

    @PlatformQuery("Get deliberation by ID")
    @RestPath("/deliberations/{id}")
    public DeliberationRecord getDeliberation(@PathParam UUID id) {
        return deliberationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Deliberation not found: " + id));
    }

    @PlatformMutation("Trigger manual deliberation")
    @RestPath("/deliberations/trigger")
    public UUID triggerDeliberation(@QueryParam("instrument") String instrument) {
        return deliberationOrchestrator.startDeliberation(instrument, "MANUAL", List.of());
    }

    @PlatformMutation("Request GDPR erasure")
    @RestPath("/gdpr/erase")
    public FsiErasureResult gdprErase(GdprErasureResource.ErasureRequest request) {
        return erasureService.erase(
                request.subjectId(),
                TenancyConstants.DEFAULT_TENANT_ID,
                request.reason());
    }

    @PlatformQuery("Get audit trail for an order")
    @RestPath("/audit/orders/{orderId}")
    public List<LedgerEntry> getOrderAuditTrail(@PathParam UUID orderId) {
        return tradingLedgerService.findByOrderId(orderId);
    }

}
