package io.casehub.fsitrading.app.compliance;

import io.casehub.api.spi.routing.RequirementStatus;
import io.casehub.api.spi.routing.TrustRoutingRequirement;
import org.jboss.logging.Logger;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FsiComplianceService {

    private static final Logger LOG = Logger.getLogger(FsiComplianceService.class);
    private static final MemoryDomain FSI_DOMAIN = new MemoryDomain("fsitrading");

    @Inject MeterRegistry meterRegistry;
    @Inject CbrCaseMemoryStore cbrCaseMemoryStore;

    public List<TrustRoutingRequirement> evaluateAll() {
        List<TrustRoutingRequirement> requirements = new ArrayList<>();
        requirements.add(evaluateMifidArt17());
        requirements.add(evaluateRts6Monitoring());
        requirements.add(evaluateRts6KillSwitch());
        requirements.add(evaluateDoddFrankAudit());
        requirements.add(evaluateMarSurveillance());
        return requirements;
    }

    TrustRoutingRequirement evaluateMifidArt17() {
        boolean hasLedgerTypes = classExists(
            "io.casehub.fsitrading.app.ledger.StrategyEvaluationLedgerEntry")
            && classExists("io.casehub.fsitrading.app.ledger.OrderExecutionLedgerEntry");
        RequirementStatus status = hasLedgerTypes
            ? RequirementStatus.CLOSED : RequirementStatus.GAP;
        return new TrustRoutingRequirement(
            "MIFID2_ART17",
            "MiFID II Article 17 — algorithmic trading decision audit",
            "Tamper-evident ledger entries with causedByEntryId chains",
            status, List.of());
    }

    TrustRoutingRequirement evaluateRts6Monitoring() {
        boolean metersExist = meterRegistry.getMeters().stream()
            .anyMatch(m -> m.getId().getName().contains("strategy.evaluation")
                        || m.getId().getName().contains("order.execution"));
        RequirementStatus status = metersExist
            ? RequirementStatus.CLOSED : RequirementStatus.GAP;
        return new TrustRoutingRequirement(
            "MIFID2_RTS6_MON",
            "MiFID II RTS 6 — real-time monitoring",
            "OTel histogram meters for strategy/order latency",
            status, List.of());
    }

    TrustRoutingRequirement evaluateRts6KillSwitch() {
        boolean classifierExists = classExists(
            "io.casehub.fsitrading.app.arena.FsiActionRiskClassifier");
        RequirementStatus status = classifierExists
            ? RequirementStatus.CLOSED : RequirementStatus.GAP;
        return new TrustRoutingRequirement(
            "MIFID2_RTS6_KILL",
            "MiFID II RTS 6 — kill switches",
            "ActionRiskClassifier gates high-risk actions via work item approval",
            status, List.of());
    }

    TrustRoutingRequirement evaluateDoddFrankAudit() {
        boolean hasLedgerService = classExists(
            "io.casehub.fsitrading.app.ledger.TradingLedgerService");
        RequirementStatus status = hasLedgerService
            ? RequirementStatus.CLOSED : RequirementStatus.GAP;
        return new TrustRoutingRequirement(
            "DODD_FRANK_AUDIT",
            "Dodd-Frank — audit trail",
            "causedByEntryId chains in tamper-evident ledger",
            status, List.of());
    }

    TrustRoutingRequirement evaluateMarSurveillance() {
        boolean hasCbrCases = false;
        try {
            CbrScanResult scanResult = cbrCaseMemoryStore.scan(
                new CbrScanRequest(null, FSI_DOMAIN, null, 1, null));
            hasCbrCases = scanResult != null && !scanResult.isEmpty();
        } catch (Exception e) {
            LOG.warn("CBR scan failed during MAR surveillance evaluation", e);
        }
        RequirementStatus status = hasCbrCases
            ? RequirementStatus.CLOSED : RequirementStatus.PARTIAL;
        return new TrustRoutingRequirement(
            "MAR_SURVEILLANCE",
            "MAR — market abuse surveillance",
            "CBR event sequence matching for pattern detection",
            status, List.of());
    }

    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
