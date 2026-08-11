package io.casehub.fsitrading.model;

import java.util.Map;

public record ConsensusResult(Map<String, InstrumentConsensus> instruments) {

    public boolean hasDeadlock() {
        return instruments.values().stream().anyMatch(InstrumentConsensus::isDeadlocked);
    }

    public boolean hasNoVoters() {
        return instruments.values().stream().anyMatch(InstrumentConsensus::hasNoVoters);
    }

    public boolean requiresHumanReview() {
        return instruments.values().stream()
                .anyMatch(ic -> ic.isDeadlocked() || ic.hasNoVoters());
    }
}
