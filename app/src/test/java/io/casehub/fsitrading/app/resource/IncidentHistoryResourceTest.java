package io.casehub.fsitrading.app.resource;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrCaseSummary;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentHistoryResourceTest {

    private CbrCaseMemoryStore cbrStore;
    private IncidentHistoryResource resource;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        resource = new IncidentHistoryResource(cbrStore);
    }

    @Test
    void returnsEmptyResultWhenNoCases() {
        when(cbrStore.scan(any())).thenReturn(new CbrScanResult(List.of(), null));

        var result = resource.history(20, null, "tenant-1");

        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void returnsCaseSummaries() {
        var summary = new CbrCaseSummary("case-1", "entity-1", "plan",
                "agent-1", 0.85, Instant.parse("2026-09-01T10:00:00Z"));
        when(cbrStore.scan(any())).thenReturn(new CbrScanResult(List.of(summary), null));

        var result = resource.history(20, null, "tenant-1");

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().caseId()).isEqualTo("case-1");
    }

    @Test
    void passesCursorForPagination() {
        when(cbrStore.scan(any())).thenReturn(new CbrScanResult(List.of(), null));

        resource.history(10, "cursor-abc", "tenant-1");

        var captor = ArgumentCaptor.forClass(CbrScanRequest.class);
        verify(cbrStore).scan(captor.capture());
        assertThat(captor.getValue().cursor()).isEqualTo("cursor-abc");
        assertThat(captor.getValue().limit()).isEqualTo(10);
    }

    @Test
    void scopesToFsitradingDomain() {
        when(cbrStore.scan(any())).thenReturn(new CbrScanResult(List.of(), null));

        resource.history(20, null, "tenant-1");

        var captor = ArgumentCaptor.forClass(CbrScanRequest.class);
        verify(cbrStore).scan(captor.capture());
        assertThat(captor.getValue().domain().name()).isEqualTo("fsitrading");
    }
}
