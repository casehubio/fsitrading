package io.casehub.fsitrading.app.agent;

import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

import java.math.BigDecimal;

public class OvernightRiskAgent extends AbstractStrategyAgent {

    private static final BigDecimal DEFAULT_QUANTITY = BigDecimal.valueOf(100);

    public OvernightRiskAgent() {
        super(StrategyType.OVERNIGHT_RISK_MANAGEMENT);
    }

    @Override
    public StrategyResponse evaluate(MarketSignal signal) {
        if ("GAP_OPEN".equals(signal.eventType()) || "CIRCUIT_BREAKER".equals(signal.eventType())
                || "FLASH_CRASH".equals(signal.eventType())) {
            return trade(signal, OrderSide.SELL, DEFAULT_QUANTITY,
                    "Overnight risk: defensive sell on " + signal.eventType());
        }
        return hold("No overnight risk trigger for " + signal.instrument());
    }
}
