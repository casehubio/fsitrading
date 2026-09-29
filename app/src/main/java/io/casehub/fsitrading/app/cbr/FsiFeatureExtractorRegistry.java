package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.spi.FsiCbrFeatureExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FsiFeatureExtractorRegistry {

    private final Map<String, FsiCbrFeatureExtractor> extractors;

    @Inject
    public FsiFeatureExtractorRegistry(Instance<FsiCbrFeatureExtractor> extractorInstances) {
        this.extractors = extractorInstances.stream()
                .collect(Collectors.toMap(FsiCbrFeatureExtractor::caseType, e -> e));
    }

    FsiFeatureExtractorRegistry(List<FsiCbrFeatureExtractor> extractorList) {
        this.extractors = extractorList.stream()
                .collect(Collectors.toMap(FsiCbrFeatureExtractor::caseType, e -> e));
    }

    public Optional<Map<String, Object>> extractFeatures(String caseType,
                                                          Map<String, Object> snapshot,
                                                          Instant detectedAt) {
        var extractor = extractors.get(caseType);
        if (extractor == null) {
            return Optional.empty();
        }
        return Optional.of(extractor.extractFromSnapshot(snapshot, detectedAt));
    }

    public Set<String> supportedCaseTypes() {
        return Set.copyOf(extractors.keySet());
    }
}
