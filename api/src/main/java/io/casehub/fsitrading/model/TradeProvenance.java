package io.casehub.fsitrading.model;

import java.util.Objects;
import java.util.UUID;

public record TradeProvenance(
        UUID deliberationChannelId,
        UUID commitmentId,
        String convergenceState,
        double confidence) {

    public TradeProvenance {
        Objects.requireNonNull(deliberationChannelId, "deliberationChannelId");
        Objects.requireNonNull(commitmentId, "commitmentId");
        Objects.requireNonNull(convergenceState, "convergenceState");
    }
}
