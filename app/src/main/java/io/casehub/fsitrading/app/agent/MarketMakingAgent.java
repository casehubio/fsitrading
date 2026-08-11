package io.casehub.fsitrading.app.agent;

import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

public class MarketMakingAgent extends AbstractStrategyAgent {

    public MarketMakingAgent() {
        super(StrategyType.MARKET_MAKING);
    }

    @Override
    public StrategyResponse evaluate(MarketSignal signal) {
        return hold("Market making requires continuous quoting — not triggered by single signals");
    }
}
