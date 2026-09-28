package io.casehub.fsitrading.app.cbr;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FsiCaseOutcomeObserverTest {

    private CbrRecordStore cbrStore;
    private FsiFeatureExtractor featureExtractor;
    private FsiCaseOutcomeObserver observer;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrRecordStore.class);
        featureExtractor = mock(FsiFeatureExtractor.class);
        observer = new FsiCaseOutcomeObserver(cbrStore, featureExtractor);
    }

    @Test
    void storesCbrPlanRecordOnCompletedOutcome() {
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

        var captor = ArgumentCaptor.forClass(CbrRecord.class);
        verify(cbrStore).store(captor.capture(), eq(CbrPlanRecord.CBR_TYPE),
                anyString(), any(), eq("tenant-1"), anyString(), any());
        assertThat(captor.getValue()).isInstanceOf(CbrPlanRecord.class);
        var stored = (CbrPlanRecord) captor.getValue();
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

    @Test
    void skipsWhenDetectedAtMissing() {
        var event = new CaseOutcomeEvent("overnight-incident", "tenant-1",
                                         UUID.randomUUID(),
                                         Map.of("instrument", "AAPL", "eventType", "FLASH_CRASH",
                                                "sector", "EQUITY", "severity", "CRITICAL"),
                                         "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        verifyNoInteractions(cbrStore);
    }

}
