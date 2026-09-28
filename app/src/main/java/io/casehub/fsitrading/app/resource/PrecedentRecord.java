package io.casehub.fsitrading.app.resource;

import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;


public record PrecedentRecord(
        String caseId,
        double similarity,
        String outcome,
        String resolutionTime) {

    public static PrecedentRecord from(CbrMatch<CbrPlanRecord> scored) {
        CbrPlanRecord plan = scored.cbrRecord();
        return new PrecedentRecord(
                scored.caseId(),
                Math.round(scored.score() * 100.0),
                plan.outcome() != null ? plan.outcome() : "Unknown",
                scored.storedAt() != null ? scored.storedAt().toString() : "");
    }
}
