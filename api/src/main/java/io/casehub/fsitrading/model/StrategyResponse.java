package io.casehub.fsitrading.model;

import java.util.List;

public sealed interface StrategyResponse {

    record Trade(List<TradeDecision> decisions, String rationale) implements StrategyResponse {
    }

    record Hold(String rationale) implements StrategyResponse {
    }
}
