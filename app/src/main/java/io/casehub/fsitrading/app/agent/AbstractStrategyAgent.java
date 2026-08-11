package io.casehub.fsitrading.app.agent;

import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.Instrument;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TradeDecision;

import java.math.BigDecimal;
import java.util.List;

public abstract class AbstractStrategyAgent {

    protected final StrategyType strategyType;

    protected AbstractStrategyAgent(StrategyType strategyType) {
        this.strategyType = strategyType;
    }

    public StrategyType strategyType() {
        return strategyType;
    }

    public abstract StrategyResponse evaluate(MarketSignal signal);

    protected StrategyResponse trade(MarketSignal signal, OrderSide side, BigDecimal quantity, String rationale) {
        var instrument = new Instrument(signal.instrument(), AssetClass.EQUITY, null);
        var decision = new TradeDecision(
                strategyType.name(), instrument, side, quantity,
                OrderType.MARKET, null, rationale);
        return new StrategyResponse.Trade(List.of(decision), rationale);
    }

    protected StrategyResponse hold(String rationale) {
        return new StrategyResponse.Hold(rationale);
    }
}
