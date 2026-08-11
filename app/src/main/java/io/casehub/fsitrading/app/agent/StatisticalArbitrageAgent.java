package io.casehub.fsitrading.app.agent;

import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

import java.math.BigDecimal;

public class StatisticalArbitrageAgent extends AbstractStrategyAgent {

    private static final BigDecimal DEFAULT_QUANTITY = BigDecimal.valueOf(40);

    public StatisticalArbitrageAgent() {
        super(StrategyType.STATISTICAL_ARBITRAGE);
    }

    @Override
    public StrategyResponse evaluate(MarketSignal signal) {
        if ("SPREAD_DIVERGENCE".equals(signal.eventType())) {
            return trade(signal, OrderSide.BUY, DEFAULT_QUANTITY,
                    "Stat arb: spread divergence on " + signal.instrument());
        }
        return hold("No arbitrage opportunity for " + signal.instrument());
    }
}
