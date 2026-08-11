package io.casehub.fsitrading.app.ledger;

import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.app.service.FillResult;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class PnlAttestationService {

    static final String ATTESTOR_ID = "fsi-pnl-system";
    static final String ATTESTOR_ROLE = "pnl-attestor";
    static final double SCALE_FACTOR = 10.0;
    static final double MIN_CONFIDENCE = 0.1;
    static final double MAX_CONFIDENCE = 1.0;

    @Inject
    LedgerEntryRepository ledgerRepo;
    @Inject
    FsiQualityDimensionScorer qualityDimensionScorer;


    public void recordOutcome(UUID evalEntryId, UUID orderId,
                              StrategyType strategyType, FillResult fillResult,
                              double recentVolatility) {
        if (!fillResult.hasRealizedPnl()) {
            return;
        }

        var attestation = new LedgerAttestation();
        attestation.ledgerEntryId = evalEntryId;
        attestation.subjectId     = orderId;
        attestation.attestorId    = ATTESTOR_ID;
        attestation.attestorType  = ActorType.SYSTEM;
        attestation.attestorRole  = ATTESTOR_ROLE;
        attestation.verdict       = fillResult.realizedPnl().signum() > 0
                                    ? AttestationVerdict.SOUND
                                    : AttestationVerdict.FLAGGED;
        attestation.confidence    = computeConfidence(fillResult.realizedPnl(), fillResult.closedNotional());
        attestation.capabilityTag = FsiActorIdentity.capabilityTag(strategyType);
        attestation.evidence      = buildEvidence(fillResult);
        attestation.occurredAt    = Instant.now();

        ledgerRepo.saveAttestation(attestation, TenancyConstants.DEFAULT_TENANT_ID);

        var scores = qualityDimensionScorer.score(fillResult, fillResult.position(), recentVolatility);
        saveDimensionAttestation(evalEntryId, orderId, strategyType,
                                 FsiQualityDimensionScorer.DIM_RETURN_MAGNITUDE, scores.returnMagnitude());
        saveDimensionAttestation(evalEntryId, orderId, strategyType,
                                 FsiQualityDimensionScorer.DIM_HOLD_PERIOD_EFFICIENCY, scores.holdPeriodEfficiency());
        saveDimensionAttestation(evalEntryId, orderId, strategyType,
                                 FsiQualityDimensionScorer.DIM_RISK_ADJUSTED_RETURN, scores.riskAdjustedReturn());
    }

    double computeConfidence(BigDecimal realizedPnl, BigDecimal closedNotional) {
        if (closedNotional == null || closedNotional.signum() == 0) {
            return MIN_CONFIDENCE;
        }
        var ratio = realizedPnl.abs()
                .divide(closedNotional, 8, RoundingMode.HALF_UP)
                .doubleValue();
        return Math.min(MAX_CONFIDENCE, Math.max(MIN_CONFIDENCE, SCALE_FACTOR * ratio));
    }

    private String buildEvidence(FillResult fillResult) {
        return String.format(
                "{\"realizedPnl\":\"%s\",\"closedNotional\":\"%s\",\"fillPrice\":\"%s\",\"closedQuantity\":\"%s\"}",
                fillResult.realizedPnl().toPlainString(),
                fillResult.closedNotional().toPlainString(),
                fillResult.fillPrice() != null ? fillResult.fillPrice().toPlainString() : "",
                fillResult.closedQuantity() != null ? fillResult.closedQuantity().toPlainString() : "");
    }

    private void saveDimensionAttestation(UUID evalEntryId, UUID orderId,
                                          StrategyType strategyType,
                                          String dimensionName, double dimensionScore) {
        var dimAttestation = new LedgerAttestation();
        dimAttestation.ledgerEntryId  = evalEntryId;
        dimAttestation.subjectId      = orderId;
        dimAttestation.attestorId     = ATTESTOR_ID;
        dimAttestation.attestorType   = ActorType.SYSTEM;
        dimAttestation.attestorRole   = ATTESTOR_ROLE;
        dimAttestation.verdict        = AttestationVerdict.SOUND;
        dimAttestation.confidence     = 1.0;
        dimAttestation.capabilityTag  = FsiActorIdentity.capabilityTag(strategyType);
        dimAttestation.trustDimension = dimensionName;
        dimAttestation.dimensionScore = dimensionScore;
        dimAttestation.occurredAt     = Instant.now();
        ledgerRepo.saveAttestation(dimAttestation, TenancyConstants.DEFAULT_TENANT_ID);
    }

}
