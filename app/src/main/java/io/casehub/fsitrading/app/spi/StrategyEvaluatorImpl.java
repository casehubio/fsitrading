package io.casehub.fsitrading.app.spi;

import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.spi.EvaluationResult;
import io.casehub.fsitrading.spi.StrategyEvaluationService;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;

@ApplicationScoped
public class StrategyEvaluatorImpl implements StrategyEvaluationService {

    @Override
    public EvaluationResult evaluate(String instrument, String strategy, String regime) {
        var marketRegime = parseRegime(regime);
        return switch (marketRegime) {
            case VOLATILE -> new EvaluationResult("HOLD", null, new BigDecimal("0.3"),
                    "High volatility — " + strategy + " recommends holding on " + instrument);
            case TRENDING -> new EvaluationResult("TRADE", OrderSide.BUY, new BigDecimal("0.7"),
                    strategy + " detects upward trend on " + instrument);
            case MEAN_REVERTING -> new EvaluationResult("TRADE", OrderSide.SELL, new BigDecimal("0.6"),
                    strategy + " detects mean reversion opportunity on " + instrument);
            case QUIET -> new EvaluationResult("HOLD", null, new BigDecimal("0.5"),
                    "Quiet market — " + strategy + " suggests waiting on " + instrument);
        };
    }

    private MarketRegime parseRegime(String regime) {
        try {
            return MarketRegime.valueOf(regime.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MarketRegime.QUIET;
        }
    }
}
