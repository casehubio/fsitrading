package io.casehub.fsitrading.app.ledger;

import io.casehub.fsitrading.FsiCapabilities;
import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.FillResult;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PnlAttestationServiceTest {

    private PnlAttestationService service;
    private LedgerEntryRepository ledgerRepo;

    @BeforeEach
    void setUp() {
        ledgerRepo = mock(LedgerEntryRepository.class);
        when(ledgerRepo.saveAttestation(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PnlAttestationService();
        service.ledgerRepo = ledgerRepo;
    }

    @Test
    void profitProducesSoundVerdict() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo).saveAttestation(captor.capture(), any());
        assertEquals(AttestationVerdict.SOUND, captor.getValue().verdict);
    }

    @Test
    void lossProducesFlaggedVerdict() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(-50), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo).saveAttestation(captor.capture(), any());
        assertEquals(AttestationVerdict.FLAGGED, captor.getValue().verdict);
    }

    @Test
    void zeroPnlProducesNoAttestation() {
        var fill = new FillResult(dummyPosition(), BigDecimal.ZERO, BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill);
        verify(ledgerRepo, never()).saveAttestation(any(), any());
    }

    @Test
    void nullPnlProducesNoAttestation() {
        var fill = new FillResult(dummyPosition(), null, null, null, null);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill);
        verify(ledgerRepo, never()).saveAttestation(any(), any());
    }

    @Test
    void attestorFieldsAreCorrect() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo).saveAttestation(captor.capture(), any());
        var att = captor.getValue();
        assertEquals("fsi-pnl-system", att.attestorId);
        assertEquals(ActorType.SYSTEM, att.attestorType);
        assertEquals("pnl-attestor", att.attestorRole);
    }

    @Test
    void capabilityTagMatchesStrategyType() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MEAN_REVERSION, fill);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo).saveAttestation(captor.capture(), any());
        assertEquals(FsiCapabilities.MEAN_REVERSION, captor.getValue().capabilityTag);
    }

    @Test
    void evidenceFieldContainsPnlData() {
        var fill = new FillResult(dummyPosition(), BigDecimal.valueOf(250), BigDecimal.valueOf(5000), BigDecimal.valueOf(50), BigDecimal.TEN);
        service.recordOutcome(UUID.randomUUID(), UUID.randomUUID(), StrategyType.MOMENTUM, fill);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo).saveAttestation(captor.capture(), any());
        var evidence = captor.getValue().evidence;
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
        service.recordOutcome(evalId, orderId, StrategyType.MOMENTUM, fill);

        var captor = org.mockito.ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledgerRepo).saveAttestation(captor.capture(), any());
        assertEquals(evalId, captor.getValue().ledgerEntryId);
        assertEquals(orderId, captor.getValue().subjectId);
    }

    private PositionEntity dummyPosition() {
        return new PositionEntity(UUID.randomUUID(), "AAPL", AssetClass.EQUITY, UUID.randomUUID());
    }
}
