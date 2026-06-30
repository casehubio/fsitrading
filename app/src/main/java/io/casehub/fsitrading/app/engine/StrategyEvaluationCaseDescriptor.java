package io.casehub.fsitrading.app.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

public final class StrategyEvaluationCaseDescriptor {

    public static final String NAMESPACE = "fsitrading";
    public static final String NAME = "strategy-evaluation";
    public static final String VERSION = "1.0.0";

    public static final BigDecimal HIGH_RISK_QUANTITY = BigDecimal.valueOf(10_000);
    public static final BigDecimal HIGH_RISK_NOTIONAL = BigDecimal.valueOf(500_000);

    public static final Duration HUMAN_APPROVAL_SLA = Duration.ofHours(1);
    public static final Duration STRATEGY_EVAL_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration ORDER_EXEC_TIMEOUT = Duration.ofSeconds(10);

    public static final Map<String, Duration> CAPABILITY_TIMEOUTS = Map.of(
            "strategy-evaluation", STRATEGY_EVAL_TIMEOUT,
            "risk-assessment", Duration.ofSeconds(5),
            "order-execution", ORDER_EXEC_TIMEOUT
    );

    private StrategyEvaluationCaseDescriptor() {}

    public static String riskLevel(BigDecimal quantity, BigDecimal notional) {
        if (quantity.compareTo(HIGH_RISK_QUANTITY) >= 0
                || notional.compareTo(HIGH_RISK_NOTIONAL) >= 0) {
            return "HIGH";
        }
        if (quantity.compareTo(HIGH_RISK_QUANTITY.divide(BigDecimal.TEN)) >= 0
                || notional.compareTo(HIGH_RISK_NOTIONAL.divide(BigDecimal.TEN)) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
