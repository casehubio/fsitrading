package io.casehub.fsitrading.model;

import java.math.BigDecimal;
import java.util.Map;

public record InstrumentConsensus(
        Status status,
        OrderSide winningSide,
        BigDecimal quantity,
        Map<OrderSide, Integer> votes) {

    public enum Status {
        CONSENSUS,
        DEADLOCKED,
        NO_VOTERS
    }

    public boolean isDeadlocked() {
        return status == Status.DEADLOCKED;
    }

    public boolean hasNoVoters() {
        return status == Status.NO_VOTERS;
    }

    public boolean isActionable() {
        return status == Status.CONSENSUS && winningSide != null;
    }
}
