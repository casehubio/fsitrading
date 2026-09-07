package io.casehub.fsitrading.app.cbr;

import io.casehub.api.spi.StepOutcomeEvent;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FsiStepOutcomeObserverTest {

    private CbrCaseMemoryStore cbrStore;
    private FsiFeatureExtractor featureExtractor;
    private FsiStepOutcomeObserver observer;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        featureExtractor = mock(FsiFeatureExtractor.class);
        observer = new FsiStepOutcomeObserver(cbrStore, featureExtractor);
    }

    @Test
    void storesCbrCaseOnSuccess() {
        when(featureExtractor.extractFromSnapshot(any(), any()))
                .thenReturn(Map.of("event_type", "FLASH_CRASH"));
        when(cbrStore.store(any(), anyString(), anyString(), any(),
                anyString(), anyString(), any())).thenReturn("step-cbr-1");

        observer.onStepOutcome(event(RoutingOutcome.SUCCESS));

        var captor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(captor.capture(), eq(PlanCbrCase.CBR_TYPE),
                anyString(), any(), eq("tenant-1"), anyString(), any());
        var stored = (PlanCbrCase) captor.getValue();
        assertThat(stored.problem()).contains("reduce-exposure");
        assertThat(stored.outcome()).isEqualTo("SUCCESS");
    }

    @Test
    void storesCbrCaseOnFailure() {
        when(featureExtractor.extractFromSnapshot(any(), any()))
                .thenReturn(Map.of("event_type", "FLASH_CRASH"));
        when(cbrStore.store(any(), anyString(), anyString(), any(),
                anyString(), anyString(), any())).thenReturn("step-cbr-2");

        observer.onStepOutcome(event(RoutingOutcome.FAILURE));

        verify(cbrStore).store(any(), eq(PlanCbrCase.CBR_TYPE),
                anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void skipsNonOvernightIncidentCaseType() {
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
        when(featureExtractor.extractFromSnapshot(any(), any()))
                .thenReturn(Map.of("event_type", "FLASH_CRASH"));
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
