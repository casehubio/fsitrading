package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.spi.FsiCbrFeatureExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FsiFeatureExtractorRegistryTest {

    private FsiFeatureExtractorRegistry registry;

    @BeforeEach
    void setUp() {
        var overnight = new StubExtractor("overnight-incident",
                Map.of("event_type", "GAP_OPEN", "severity", "HIGH"));
        var flashCrash = new StubExtractor("flash-crash",
                Map.of("event_type", "FLASH_CRASH", "volatility", 8.5));
        registry = new FsiFeatureExtractorRegistry(List.of(overnight, flashCrash));
    }

    @Test
    void dispatchesByCaseType() {
        var features = registry.extractFeatures("overnight-incident",
                Map.of(), Instant.now());

        assertThat(features).isPresent();
        assertThat(features.get()).containsEntry("event_type", "GAP_OPEN");
    }

    @Test
    void dispatchesToCorrectExtractor() {
        var features = registry.extractFeatures("flash-crash",
                Map.of(), Instant.now());

        assertThat(features).isPresent();
        assertThat(features.get()).containsEntry("event_type", "FLASH_CRASH");
        assertThat(features.get()).containsEntry("volatility", 8.5);
    }

    @Test
    void returnsEmptyForUnknownCaseType() {
        var features = registry.extractFeatures("unknown-type",
                Map.of(), Instant.now());

        assertThat(features).isEmpty();
    }

    @Test
    void listsRegisteredCaseTypes() {
        assertThat(registry.supportedCaseTypes())
                .containsExactlyInAnyOrder("overnight-incident", "flash-crash");
    }

    static class StubExtractor implements FsiCbrFeatureExtractor {
        private final String caseType;
        private final Map<String, Object> features;

        StubExtractor(String caseType, Map<String, Object> features) {
            this.caseType = caseType;
            this.features = features;
        }

        @Override
        public String caseType() {
            return caseType;
        }

        @Override
        public Map<String, Object> extractFromSnapshot(Map<String, Object> snapshot,
                                                        Instant detectedAt) {
            return features;
        }
    }
}
