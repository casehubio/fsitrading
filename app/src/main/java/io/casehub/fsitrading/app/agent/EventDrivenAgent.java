package io.casehub.fsitrading.app.agent;

import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

import java.math.BigDecimal;

public class EventDrivenAgent extends AbstractStrategyAgent {

    private static final BigDecimal DEFAULT_QUANTITY = BigDecimal.valueOf(25);

    public EventDrivenAgent() {
        super(StrategyType.EVENT_DRIVEN);
    }

    @Override
    public StrategyResponse evaluate(MarketSignal signal) {
        if ("NEWS_EVENT".equals(signal.eventType()) || "FLASH_CRASH".equals(signal.eventType())) {
            return trade(signal, OrderSide.SELL, DEFAULT_QUANTITY,
                    "Event-driven: reactive to " + signal.eventType() + " on " + signal.instrument());
        }
        return hold("No event signal for " + signal.instrument());
    }
}
