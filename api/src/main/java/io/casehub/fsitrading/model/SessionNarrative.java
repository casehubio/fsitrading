package io.casehub.fsitrading.model;

import java.time.Instant;
import java.util.List;

public record SessionNarrative(
        List<String> instruments,
        String narrative,
        Instant timestamp) {
}
