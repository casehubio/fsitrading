package io.casehub.fsitrading.app.resource;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimilarIncidentResourceTest {

    private CbrCaseMemoryStore cbrStore;
    private SimilarIncidentResource resource;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        resource = new SimilarIncidentResource(cbrStore);
    }

    @Test
    void returnsEmptyListWhenNoSimilarCases() {
        when(cbrStore.retrieveSimilar(any(), eq(PlanCbrCase.class)))
                .thenReturn(List.of());
        var result = resource.findSimilar("case-123", "tenant-1");
        assertThat(result).isEmpty();
    }

    @Test
    void returnsPrecedentRecords() {
        var planCase = new PlanCbrCase("incident", "response", "Resolved", null,
                Map.of(), List.of(), 0.8, "agent");
        var scored = new ScoredCbrCase<>(planCase, "case-1", 0.85, false,
                Map.of(), Instant.parse("2026-09-01T10:00:00Z"), null, null);
        when(cbrStore.retrieveSimilar(any(), eq(PlanCbrCase.class)))
                .thenReturn(List.of(scored));

        var result = resource.findSimilar("case-123", "tenant-1");

        assertThat(result).hasSize(1);
        PrecedentRecord precedent = result.getFirst();
        assertThat(precedent.caseId()).isEqualTo("case-1");
        assertThat(precedent.similarity()).isEqualTo(85.0);
        assertThat(precedent.outcome()).isEqualTo("Resolved");
        assertThat(precedent.resolutionTime()).isEqualTo("2026-09-01T10:00:00Z");
    }

    @Test
    void nullOutcomeMapsToUnknown() {
        var planCase = new PlanCbrCase("incident", "response", null, null,
                Map.of(), List.of(), 0.8, "agent");
        var scored = new ScoredCbrCase<>(planCase, "case-2", 0.5);
        when(cbrStore.retrieveSimilar(any(), eq(PlanCbrCase.class)))
                .thenReturn(List.of(scored));

        var result = resource.findSimilar("case-123", "tenant-1");

        assertThat(result.getFirst().outcome()).isEqualTo("Unknown");
        assertThat(result.getFirst().resolutionTime()).isEmpty();
    }
}
