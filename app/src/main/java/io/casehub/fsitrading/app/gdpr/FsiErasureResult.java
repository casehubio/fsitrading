package io.casehub.fsitrading.app.gdpr;

import io.casehub.ledger.runtime.privacy.LedgerErasureService;

public record FsiErasureResult(
    String traderId,
    int memoriesErased,
    int cbrCasesErased,
    LedgerErasureService.ErasureResult ledgerResult) {}
