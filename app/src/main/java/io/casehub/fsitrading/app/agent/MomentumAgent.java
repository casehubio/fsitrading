package io.casehub.fsitrading.app.agent;

import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

import java.math.BigDecimal;

public class MomentumAgent extends AbstractStrategyAgent {

    private static final BigDecimal DEFAULT_QUANTITY = BigDecimal.valueOf(50);

    public MomentumAgent() {
        super(StrategyType.MOMENTUM);
    }

    @Override
    public StrategyResponse evaluate(MarketSignal signal) {
        if ("PRICE_MOVEMENT".equals(signal.eventType()) && signal.price() != null) {
            return trade(signal, OrderSide.BUY, DEFAULT_QUANTITY,
                    "Momentum detected: price movement on " + signal.instrument());
        }
        return hold("No momentum signal detected for " + signal.instrument());
    }
}
