package io.casehub.fsitrading.app.gdpr;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FsiGdprErasureServiceTest {

    private CaseMemoryStore caseMemoryStore;
    private CbrCaseMemoryStore cbrCaseMemoryStore;
    private LedgerErasureService ledgerErasureService;
    private FsiGdprErasureService service;

    @BeforeEach
    void setUp() {
        caseMemoryStore = mock(CaseMemoryStore.class);
        cbrCaseMemoryStore = mock(CbrCaseMemoryStore.class);
        ledgerErasureService = mock(LedgerErasureService.class);
        service = new FsiGdprErasureService();
        service.caseMemoryStore = caseMemoryStore;
        service.cbrCaseMemoryStore = cbrCaseMemoryStore;
        service.ledgerErasureService = ledgerErasureService;
    }

    @Test
    void erasesAllThreeStoresAndReturnsAggregateResult() {
        when(caseMemoryStore.eraseEntity("trader-1", "tenant-1")).thenReturn(3);
        when(cbrCaseMemoryStore.erase(any(EraseRequest.class))).thenReturn(2);
        when(ledgerErasureService.erase("trader-1", ErasureReason.GDPR_ART_17_REQUEST))
            .thenReturn(new LedgerErasureService.ErasureResult(
                "trader-1", true, 5, Optional.of(UUID.randomUUID())));

        FsiErasureResult result = service.erase("trader-1", "tenant-1",
            ErasureReason.GDPR_ART_17_REQUEST);

        assertThat(result.traderId()).isEqualTo("trader-1");
        assertThat(result.memoriesErased()).isEqualTo(3);
        assertThat(result.cbrCasesErased()).isEqualTo(2);
        assertThat(result.ledgerResult().mappingFound()).isTrue();
        assertThat(result.ledgerResult().affectedEntryCount()).isEqualTo(5);
    }

    @Test
    void cbrErasureUsesDomainScopedEraseRequest() {
        when(caseMemoryStore.eraseEntity("trader-1", "tenant-1")).thenReturn(0);
        when(cbrCaseMemoryStore.erase(any(EraseRequest.class))).thenReturn(0);
        when(ledgerErasureService.erase("trader-1", ErasureReason.GDPR_ART_17_REQUEST))
            .thenReturn(new LedgerErasureService.ErasureResult(
                "trader-1", false, 0, Optional.empty()));

        service.erase("trader-1", "tenant-1", ErasureReason.GDPR_ART_17_REQUEST);

        ArgumentCaptor<EraseRequest> captor = ArgumentCaptor.forClass(EraseRequest.class);
        verify(cbrCaseMemoryStore).erase(captor.capture());
        EraseRequest req = captor.getValue();
        assertThat(req.entityId()).isEqualTo("trader-1");
        assertThat(req.domain().name()).isEqualTo("fsitrading");
        assertThat(req.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void retryAfterFullErasureReturnsZeros() {
        when(caseMemoryStore.eraseEntity("trader-1", "tenant-1")).thenReturn(0);
        when(cbrCaseMemoryStore.erase(any(EraseRequest.class))).thenReturn(0);
        when(ledgerErasureService.erase("trader-1", ErasureReason.GDPR_ART_17_REQUEST))
            .thenReturn(new LedgerErasureService.ErasureResult(
                "trader-1", false, 0, Optional.of(UUID.randomUUID())));

        FsiErasureResult result = service.erase("trader-1", "tenant-1",
            ErasureReason.GDPR_ART_17_REQUEST);

        assertThat(result.memoriesErased()).isZero();
        assertThat(result.cbrCasesErased()).isZero();
        assertThat(result.ledgerResult().mappingFound()).isFalse();
    }
}
