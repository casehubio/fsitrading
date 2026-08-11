package io.casehub.fsitrading.app.agent;

import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

public class PortfolioRebalanceAgent extends AbstractStrategyAgent {

    public PortfolioRebalanceAgent() {
        super(StrategyType.PORTFOLIO_REBALANCE);
    }

    @Override
    public StrategyResponse evaluate(MarketSignal signal) {
        return hold("Portfolio rebalance operates on schedule, not single signals");
    }
}
