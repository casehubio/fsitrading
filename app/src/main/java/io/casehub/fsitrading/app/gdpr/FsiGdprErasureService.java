package io.casehub.fsitrading.app.gdpr;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FsiGdprErasureService {

    private static final MemoryDomain FSI_DOMAIN = new MemoryDomain("fsitrading");

    @Inject CaseMemoryStore caseMemoryStore;
    @Inject CbrCaseMemoryStore cbrCaseMemoryStore;
    @Inject LedgerErasureService ledgerErasureService;

    public FsiErasureResult erase(String traderId, String tenantId, ErasureReason reason) {
        int memoriesErased = caseMemoryStore.eraseEntity(traderId, tenantId);

        int cbrCasesErased = cbrCaseMemoryStore.erase(
            new EraseRequest(traderId, FSI_DOMAIN, tenantId, null));

        LedgerErasureService.ErasureResult ledgerResult =
            ledgerErasureService.erase(traderId, reason);

        return new FsiErasureResult(traderId, memoriesErased, cbrCasesErased, ledgerResult);
    }
}
