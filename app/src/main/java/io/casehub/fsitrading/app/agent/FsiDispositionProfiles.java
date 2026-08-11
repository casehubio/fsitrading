package io.casehub.fsitrading.app.agent;

import io.casehub.eidos.api.AgentDisposition;
import io.casehub.fsitrading.model.StrategyType;

import java.util.Map;

public final class FsiDispositionProfiles {

    private FsiDispositionProfiles() {}

    private static final Map<StrategyType, AgentDisposition> DISPOSITIONS = Map.of(
            StrategyType.MOMENTUM,
            disposition("aggressive", "moderate", "independent"),
            StrategyType.MEAN_REVERSION,
            disposition("moderate", "high", "collaborative"),
            StrategyType.STATISTICAL_ARBITRAGE,
            disposition("moderate", "high", "independent"),
            StrategyType.MARKET_MAKING,
            disposition("conservative", "high", "independent"),
            StrategyType.EVENT_DRIVEN,
            disposition("aggressive", "low", "independent"),
            StrategyType.PORTFOLIO_REBALANCE,
            disposition("conservative", "high", "collaborative"),
            StrategyType.OVERNIGHT_RISK_MANAGEMENT,
            disposition("conservative", "high", "independent"));

    public static AgentDisposition forType(StrategyType type) {
        return DISPOSITIONS.get(type);
    }

    private static AgentDisposition disposition(String riskAppetite, String ruleFollowing,
                                                String socialOrientation) {
        return AgentDisposition.builder()
                               .riskAppetite(riskAppetite)
                               .ruleFollowing(ruleFollowing)
                               .socialOrient(socialOrientation)
                               .build();
    }
}
