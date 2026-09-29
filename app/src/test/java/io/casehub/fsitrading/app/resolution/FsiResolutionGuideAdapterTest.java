package io.casehub.fsitrading.app.resolution;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FsiResolutionGuideAdapterTest {

    private static FsiResolutionGuideAdapter adapter;

    @BeforeAll
    static void setUp() {
        adapter = new FsiResolutionGuideAdapter();
    }

    @Test
    void idReturnsFsiTradingResolutions() {
        assertThat(adapter.id()).isEqualTo("fsi-trading-resolutions");
    }

    @Test
    void discoversAllFiveResolutionDocuments() {
        var guides = adapter.discover("test-tenant");
        assertThat(guides).hasSize(5);
    }

    @Test
    void allGuidesHaveRequiredFields() {
        var guides = adapter.discover("test-tenant");
        for (var guide : guides) {
            assertThat(guide.documentId()).as("documentId").isNotBlank();
            assertThat(guide.problem()).as("problem for %s", guide.documentId()).isNotBlank();
            assertThat(guide.solution()).as("solution for %s", guide.documentId()).isNotBlank();
            assertThat(guide.features()).as("features for %s", guide.documentId()).isNotEmpty();
            assertThat(guide.domain()).as("domain for %s", guide.documentId()).isEqualTo("fsi-trading");
            assertThat(guide.steps()).as("steps for %s", guide.documentId()).isNotEmpty();
        }
    }

    @Test
    void counterpartyDefaultDocumentExists() {
        var guides = adapter.discover("test-tenant");
        var counterparty = guides.stream()
                .filter(g -> g.documentId().equals("counterparty-default"))
                .findFirst();
        assertThat(counterparty).isPresent();
        assertThat(counterparty.get().features()).containsKey("severity");
        assertThat(counterparty.get().features()).containsKey("category");
    }

    @Test
    void discoverIsIdempotent() {
        var first = adapter.discover("test-tenant");
        var second = adapter.discover("test-tenant");
        assertThat(first).isEqualTo(second);
    }
}
