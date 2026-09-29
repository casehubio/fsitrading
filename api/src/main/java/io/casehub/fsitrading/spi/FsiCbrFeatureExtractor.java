package io.casehub.fsitrading.spi;

import java.time.Instant;
import java.util.Map;

public interface FsiCbrFeatureExtractor {

    String caseType();

    Map<String, Object> extractFromSnapshot(Map<String, Object> snapshot, Instant detectedAt);
}
