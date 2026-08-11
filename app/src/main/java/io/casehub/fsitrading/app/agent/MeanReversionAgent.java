package io.casehub.fsitrading.app.agent;

import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

import java.math.BigDecimal;

public class MeanReversionAgent extends AbstractStrategyAgent {

    private static final BigDecimal DEFAULT_QUANTITY = BigDecimal.valueOf(30);

    public MeanReversionAgent() {
        super(StrategyType.MEAN_REVERSION);
    }

    @Override
    public StrategyResponse evaluate(MarketSignal signal) {
        if ("PRICE_MOVEMENT".equals(signal.eventType())) {
            return trade(signal, OrderSide.SELL, DEFAULT_QUANTITY,
                    "Mean reversion: price overextended on " + signal.instrument());
        }
        return hold("No reversion signal for " + signal.instrument());
    }
}
