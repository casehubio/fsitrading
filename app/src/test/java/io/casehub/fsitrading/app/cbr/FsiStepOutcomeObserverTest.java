package io.casehub.fsitrading.app.cbr;

import io.casehub.api.spi.StepOutcomeEvent;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
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

class FsiStepOutcomeObserverTest {

    private CbrRecordStore              cbrStore;
    private FsiFeatureExtractorRegistry extractorRegistry;
    private FsiStepOutcomeObserver      observer;

    @BeforeEach
    void setUp() {
        cbrStore          = mock(CbrRecordStore.class);
        extractorRegistry = mock(FsiFeatureExtractorRegistry.class);
        observer          = new FsiStepOutcomeObserver(cbrStore, extractorRegistry);
    }

    @Test
    void storesCbrCaseOnSuccess() {
        when(extractorRegistry.extractFeatures(eq("overnight-incident"), any(), any()))
                .thenReturn(java.util.Optional.of(Map.of("event_type", "FLASH_CRASH")));
        when(cbrStore.store(any(), anyString(), anyString(), any(),
                            anyString(), anyString(), any())).thenReturn("step-cbr-1");

        observer.onStepOutcome(event(RoutingOutcome.SUCCESS));

        var captor = ArgumentCaptor.forClass(CbrRecord.class);
        verify(cbrStore).store(captor.capture(), eq(CbrFeatureRecord.CBR_TYPE),
                               anyString(), any(), eq("tenant-1"), anyString(), any());
        var stored = (CbrFeatureRecord) captor.getValue();
        assertThat(stored.problem()).contains("reduce-exposure");
        assertThat(stored.outcome()).isEqualTo("SUCCESS");
    }

    @Test
    void storesCbrCaseOnFailure() {
        when(extractorRegistry.extractFeatures(eq("overnight-incident"), any(), any()))
                .thenReturn(java.util.Optional.of(Map.of("event_type", "FLASH_CRASH")));
        when(cbrStore.store(any(), anyString(), anyString(), any(),
                            anyString(), anyString(), any())).thenReturn("step-cbr-2");

        observer.onStepOutcome(event(RoutingOutcome.FAILURE));

        verify(cbrStore).store(any(), eq(CbrFeatureRecord.CBR_TYPE),
                               anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void skipsUnknownCaseType() {
        when(extractorRegistry.extractFeatures(eq("other-case"), any(), any()))
                .thenReturn(java.util.Optional.empty());

        var event = new StepOutcomeEvent(UUID.randomUUID(), "tenant-1",
                                         "other-case", "step-1", "cap-1", "worker-1",
                                         RoutingOutcome.SUCCESS,
                                         Map.of("instrument", "AAPL", "detectedAt", "2026-09-01T14:30:00Z"),
                                         Duration.ofSeconds(5));

        observer.onStepOutcome(event);

        verifyNoInteractions(cbrStore);
    }

    @Test
    void skipsWhenDetectedAtMissing() {
        var event = new StepOutcomeEvent(UUID.randomUUID(), "tenant-1",
                                         "overnight-incident", "step-1", "cap-1", "worker-1",
                                         RoutingOutcome.SUCCESS,
                                         Map.of("instrument", "AAPL"),
                                         Duration.ofSeconds(5));

        observer.onStepOutcome(event);

        verifyNoInteractions(cbrStore);
    }

    @Test
    void recordsOutcomeAfterStore() {
        when(extractorRegistry.extractFeatures(eq("overnight-incident"), any(), any()))
                .thenReturn(java.util.Optional.of(Map.of("event_type", "FLASH_CRASH")));
        when(cbrStore.store(any(), anyString(), anyString(), any(),
                            anyString(), anyString(), any())).thenReturn("step-cbr-1");

        observer.onStepOutcome(event(RoutingOutcome.SUCCESS));

        verify(cbrStore).recordOutcome(eq("step-cbr-1"), eq("tenant-1"), any());
    }

    private StepOutcomeEvent event(RoutingOutcome outcome) {
        return new StepOutcomeEvent(UUID.randomUUID(), "tenant-1",
                                    "overnight-incident", "reduce-exposure", "risk-management",
                                    "MomentumStrategy",
                                    outcome,
                                    Map.of("instrument", "AAPL", "eventType", "FLASH_CRASH",
                                           "sector", "EQUITY", "severity", "CRITICAL",
                                           "detectedAt", "2026-09-01T14:30:00Z"),
                                    Duration.ofSeconds(5));
    }
}
