package io.casehub.fsitrading.app.resource;

import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;


public record PrecedentRecord(
        String caseId,
        double similarity,
        String outcome,
        String resolutionTime) {

    public static PrecedentRecord from(ScoredCbrCase<PlanCbrCase> scored) {
        PlanCbrCase plan = scored.cbrCase();
        return new PrecedentRecord(
                scored.caseId(),
                Math.round(scored.score() * 100.0),
                plan.outcome() != null ? plan.outcome() : "Unknown",
                scored.storedAt() != null ? scored.storedAt().toString() : "");
    }
}
