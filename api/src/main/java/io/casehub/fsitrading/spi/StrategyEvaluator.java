package io.casehub.fsitrading.spi;

import io.casehub.fsitrading.model.Instrument;
import io.casehub.fsitrading.model.TradeDecision;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public interface StrategyEvaluator {

    Optional<TradeDecision> evaluate(String strategyId,
                                     Instrument instrument,
                                     BigDecimal currentPrice,
                                     Map<String, Object> marketContext);
}
