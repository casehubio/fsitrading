package io.casehub.fsitrading.app.ledger;

import io.casehub.fsitrading.FsiCapabilities;
import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.FillResult;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PnlAttestationServiceTest {

    private PnlAttestationService service;
    private LedgerEntryRepository ledgerRepo;

    @BeforeEach
    void setUp() {
        ledgerRepo = mock(LedgerEntryRepository.class);
        when(ledgerRepo.saveAttestation(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PnlAttestationService();
        service.ledgerRepo = ledgerRepo;
        service.qualityDimensionScorer = new FsiQualityDimensionScorer();
    }

    @Test
    void profitProducesSoundVerdict() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill, 0.02);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo, times(4)).saveAttestation(captor.capture(), any());
        assertEquals(AttestationVerdict.SOUND, captor.getAllValues().get(0).verdict);
    }

    @Test
    void lossProducesFlaggedVerdict() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(-50), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill, 0.02);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo, times(4)).saveAttestation(captor.capture(), any());
        assertEquals(AttestationVerdict.FLAGGED, captor.getAllValues().get(0).verdict);
    }

    @Test
    void zeroPnlProducesNoAttestation() {
        var fill = new FillResult(dummyPosition(), BigDecimal.ZERO, BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill, 0.02);
        verify(ledgerRepo, never()).saveAttestation(any(), any());
    }

    @Test
    void nullPnlProducesNoAttestation() {
        var fill = new FillResult(dummyPosition(), null, null, null, null);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill, 0.02);
        verify(ledgerRepo, never()).saveAttestation(any(), any());
    }

    @Test
    void attestorFieldsAreCorrect() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill, 0.02);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo, times(4)).saveAttestation(captor.capture(), any());
        var att = captor.getAllValues().get(0);
        assertEquals("fsi-pnl-system", att.attestorId);
        assertEquals(ActorType.SYSTEM, att.attestorType);
        assertEquals("pnl-attestor", att.attestorRole);
    }

    @Test
    void capabilityTagMatchesStrategyType() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MEAN_REVERSION, fill, 0.02);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo, times(4)).saveAttestation(captor.capture(), any());
        assertEquals(FsiCapabilities.MEAN_REVERSION, captor.getAllValues().get(0).capabilityTag);
    }

    @Test
    void evidenceFieldContainsPnlData() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(250), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill, 0.02);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo, times(4)).saveAttestation(captor.capture(), any());
        var evidence = captor.getAllValues().get(0).evidence;
        assertNotNull(evidence);
        assertTrue(evidence.contains("250"));
        assertTrue(evidence.contains("5000"));
    }

    @Test
    void confidenceClampedToMinimum() {
        assertEquals(0.1, service.computeConfidence(BigDecimal.valueOf(1), BigDecimal.valueOf(10000)), 0.01);
    }

    @Test
    void confidenceScalesWithMagnitude() {
        double conf5pct = service.computeConfidence(BigDecimal.valueOf(250), BigDecimal.valueOf(5000));
        assertEquals(0.5, conf5pct, 0.01);
    }

    @Test
    void confidenceClampedToMaximum() {
        double conf = service.computeConfidence(BigDecimal.valueOf(2000), BigDecimal.valueOf(5000));
        assertEquals(1.0, conf, 0.01);
    }

    @Test
    void confidenceWithZeroNotionalReturnsMinimum() {
        assertEquals(0.1, service.computeConfidence(BigDecimal.valueOf(100), BigDecimal.ZERO), 0.01);
    }

    @Test
    void subjectIdAndLedgerEntryIdPassedCorrectly() {
        var evalId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(evalId, orderId, StrategyType.MOMENTUM, fill, 0.02);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo, times(4)).saveAttestation(captor.capture(), any());
        assertEquals(evalId, captor.getAllValues().get(0).ledgerEntryId);
        assertEquals(orderId, captor.getAllValues().get(0).subjectId);
    }

    @Test
    void writesThreeDimensionAttestations() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(1000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill, 0.02);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo, times(4)).saveAttestation(captor.capture(), any());

        var attestations = captor.getAllValues();
        assertNull(attestations.get(0).trustDimension);

        assertEquals(FsiQualityDimensionScorer.DIM_RETURN_MAGNITUDE, attestations.get(1).trustDimension);
        assertNotNull(attestations.get(1).dimensionScore);
        assertTrue(attestations.get(1).dimensionScore >= 0.0 && attestations.get(1).dimensionScore <= 1.0);

        assertEquals(FsiQualityDimensionScorer.DIM_HOLD_PERIOD_EFFICIENCY, attestations.get(2).trustDimension);
        assertNotNull(attestations.get(2).dimensionScore);

        assertEquals(FsiQualityDimensionScorer.DIM_RISK_ADJUSTED_RETURN, attestations.get(3).trustDimension);
        assertNotNull(attestations.get(3).dimensionScore);
    }


    private PositionEntity dummyPosition() {
        var pos = new PositionEntity(UUID.randomUUID(), "AAPL", AssetClass.EQUITY, UUID.randomUUID());
        pos.setQuantity(BigDecimal.valueOf(100));
        pos.setOpenedAt(java.time.Instant.now().minus(java.time.Duration.ofMinutes(30)));
        return pos;
    }
}
