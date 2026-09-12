package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.cbr.FsiFeatureExtractor;
import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.spi.IncidentStore;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.ResolvedCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimilarIncidentResourceTest {

    private CbrCaseMemoryStore cbrStore;
    private IncidentStore incidentStore;
    private FsiFeatureExtractor featureExtractor;
    private SimilarIncidentResource resource;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        incidentStore = mock(IncidentStore.class);
        featureExtractor = mock(FsiFeatureExtractor.class);
        resource = new SimilarIncidentResource(cbrStore, incidentStore, featureExtractor);
    }

    @Test
    void returnsEmptyListWhenCaseNotFound() {
        when(incidentStore.findByCaseId(any())).thenReturn(null);
        var result = resource.findSimilar(UUID.randomUUID().toString(), "tenant-1");
        assertThat(result).isEmpty();
    }

    @Test
    void usesIncidentFeaturesInCbrQuery() {
        var caseId = UUID.randomUUID();
        var incident = new IncidentRecord(caseId, IncidentSeverity.CRITICAL,
                MarketEventType.FLASH_CRASH, List.of("AAPL"), "RESOLVED",
                Instant.parse("2026-09-01T14:30:00Z"), null, null, null);
        when(incidentStore.findByCaseId(caseId)).thenReturn(incident);
        when(featureExtractor.extractFromSnapshot(any(), any()))
                .thenReturn(Map.of("event_type", "FLASH_CRASH", "instrument_sector", "EQUITY"));
        when(cbrStore.retrieveSimilar(any(), eq(ResolvedCase.class)))
                .thenReturn(List.of());

        resource.findSimilar(caseId.toString(), "tenant-1");

        var queryCaptor = ArgumentCaptor.forClass(CbrQuery.class);
        verify(cbrStore).retrieveSimilar(queryCaptor.capture(), eq(ResolvedCase.class));
        assertThat(queryCaptor.getValue().features()).isNotEmpty();
    }

    @Test
    void returnsPrecedentRecords() {
        var caseId = UUID.randomUUID();
        var incident = new IncidentRecord(caseId, IncidentSeverity.HIGH,
                MarketEventType.LIQUIDITY_DROP, List.of("MSFT"), "ACTIVE",
                Instant.parse("2026-09-01T14:30:00Z"), null, null, null);
        when(incidentStore.findByCaseId(caseId)).thenReturn(incident);
        when(featureExtractor.extractFromSnapshot(any(), any()))
                .thenReturn(Map.of("event_type", "LIQUIDITY_DROP"));

        var planCase = new ResolvedCase("incident", "response", "Resolved", null,
                Map.of(), List.of(), 0.8, "agent");
        var scored = new ScoredCbrCase<>(planCase, "case-1", ResolvedCase.CBR_TYPE, 0.85, false,
                Map.of(), Instant.parse("2026-09-01T10:00:00Z"), null, null);
        when(cbrStore.retrieveSimilar(any(), eq(ResolvedCase.class)))
                .thenReturn(List.of(scored));

        var result = resource.findSimilar(caseId.toString(), "tenant-1");

        assertThat(result).hasSize(1);
        PrecedentRecord precedent = result.getFirst();
        assertThat(precedent.caseId()).isEqualTo("case-1");
        assertThat(precedent.similarity()).isEqualTo(85.0);
        assertThat(precedent.outcome()).isEqualTo("Resolved");
    }

    @Test
    void nullOutcomeMapsToUnknown() {
        var caseId = UUID.randomUUID();
        var incident = new IncidentRecord(caseId, IncidentSeverity.MEDIUM,
                MarketEventType.PRICE_TICK, List.of("GOOGL"), "ACTIVE",
                Instant.parse("2026-09-01T14:30:00Z"), null, null, null);
        when(incidentStore.findByCaseId(caseId)).thenReturn(incident);
        when(featureExtractor.extractFromSnapshot(any(), any()))
                .thenReturn(Map.of("event_type", "PRICE_TICK"));

        var planCase = new ResolvedCase("incident", "response", null, null,
                Map.of(), List.of(), 0.8, "agent");
        var scored = new ScoredCbrCase<>(planCase, "case-2", ResolvedCase.CBR_TYPE, 0.5);
        when(cbrStore.retrieveSimilar(any(), eq(ResolvedCase.class)))
                .thenReturn(List.of(scored));

        var result = resource.findSimilar(caseId.toString(), "tenant-1");

        assertThat(result.getFirst().outcome()).isEqualTo("Unknown");
    }
}
