package io.casehub.fsitrading.app.incident;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.ClassificationContext;
import io.casehub.api.spi.RiskClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.api.spi.routing.StaticSetStrategy;
import io.casehub.worker.api.PlannedAction;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;

@ApplicationScoped
@RiskClassifier
public class FsiActionRiskClassifier implements ActionRiskClassifier {

    private static final double       HIGH_THRESHOLD = 0.25;
    private static final RiskDecision AUTONOMOUS     = new RiskDecision.Autonomous();

    @Override
    public RiskDecision classify(PlannedAction action, ClassificationContext context) {
        String actionType = action.actionType();

        if ("counterparty-close".equals(actionType)) {
            return gate("Counterparty exposure close requires approval", Duration.ofMinutes(15));
        }

        if ("full-liquidation".equals(actionType)) {
            return gate("Full liquidation requires approval", Duration.ofMinutes(5));
        }

        if ("new-position".equals(actionType)) {
            return AUTONOMOUS;
        }

        double ratio = extractPortfolioRatio(action);
        if (ratio > HIGH_THRESHOLD) {
            return gate("Close > 25% portfolio: " + String.format("%.1f%%", ratio * 100),
                        Duration.ofMinutes(15));
        }

        return AUTONOMOUS;
    }

    private double extractPortfolioRatio(PlannedAction action) {
        Object ratio = action.parameters().get("portfolioRatio");
        if (ratio instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    private RiskDecision.GateRequired gate(String reason, Duration expiresIn) {
        return new RiskDecision.GateRequired(reason, false,
                                             StaticSetStrategy.of("fsi-oncall"),
                                             expiresIn, "fsitrading", null, null);
    }
}
