package io.casehub.fsitrading.app.cbr;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FsiCaseOutcomeObserverTest {

    private CbrCaseMemoryStore cbrStore;
    private FsiFeatureExtractor featureExtractor;
    private FsiCaseOutcomeObserver observer;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        featureExtractor = mock(FsiFeatureExtractor.class);
        observer = new FsiCaseOutcomeObserver(cbrStore, featureExtractor);
    }

    @Test
    void storesPlanCbrCaseOnCompletedOutcome() {
        when(featureExtractor.extractFromSnapshot(any(), any()))
                .thenReturn(Map.of("event_type", "FLASH_CRASH",
                        "instrument_sector", "EQUITY"));
        when(cbrStore.store(any(), anyString(), anyString(), any(),
                anyString(), anyString(), any())).thenReturn("cbr-case-1");

        var event = new CaseOutcomeEvent("overnight-incident", "tenant-1",
                UUID.randomUUID(),
                Map.of("instrument", "AAPL", "eventType", "FLASH_CRASH",
                        "sector", "EQUITY", "severity", "CRITICAL",
                        "detectedAt", "2026-09-01T14:30:00Z"),
                "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        var captor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(captor.capture(), eq(PlanCbrCase.CBR_TYPE),
                anyString(), any(), eq("tenant-1"), anyString(), any());
        assertThat(captor.getValue()).isInstanceOf(PlanCbrCase.class);
        var stored = (PlanCbrCase) captor.getValue();
        assertThat(stored.problem()).contains("CRITICAL");
        assertThat(stored.producerAgentId()).isEqualTo("fsi-incident-cbr");
    }

    @Test
    void skipsNonOvernightIncidentCaseTypes() {
        var event = new CaseOutcomeEvent("other-case", "tenant-1",
                UUID.randomUUID(), Map.of(), "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verifyNoInteractions(cbrStore);
        verifyNoInteractions(featureExtractor);
    }

    @Test
    void skipsFaultedOutcomes() {
        var event = new CaseOutcomeEvent("overnight-incident", "tenant-1",
                UUID.randomUUID(),
                Map.of("instrument", "AAPL", "eventType", "FLASH_CRASH",
                        "sector", "EQUITY", "severity", "CRITICAL",
                        "detectedAt", "2026-09-01T14:30:00Z"),
                "FAULTED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verifyNoInteractions(cbrStore);
    }

    @Test
    void recordsOutcomeAfterStore() {
        when(featureExtractor.extractFromSnapshot(any(), any()))
                .thenReturn(Map.of("event_type", "FLASH_CRASH"));
        when(cbrStore.store(any(), anyString(), anyString(), any(),
                anyString(), anyString(), any())).thenReturn("cbr-case-1");

        var event = new CaseOutcomeEvent("overnight-incident", "tenant-1",
                UUID.randomUUID(),
                Map.of("instrument", "AAPL", "eventType", "FLASH_CRASH",
                        "sector", "EQUITY", "severity", "HIGH",
                        "detectedAt", "2026-09-01T14:30:00Z"),
                "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verify(cbrStore).recordOutcome(anyString(), eq("tenant-1"), any());
    }
}
