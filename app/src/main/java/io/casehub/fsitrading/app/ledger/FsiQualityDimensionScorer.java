package io.casehub.fsitrading.app.ledger;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.FillResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class FsiQualityDimensionScorer {

    public static final String DIM_RETURN_MAGNITUDE = "return-magnitude";
    public static final String DIM_HOLD_PERIOD_EFFICIENCY = "hold-period-efficiency";
    public static final String DIM_RISK_ADJUSTED_RETURN = "risk-adjusted-return";

    static final double SIGMOID_SCALE_HOLD = 0.1;
    static final double SIGMOID_SCALE_RISK = 1.0;
    static final double LOW_VOLATILITY_THRESHOLD = 0.0001;

    public record QualityScores(double returnMagnitude, double holdPeriodEfficiency, double riskAdjustedReturn) {}

    public QualityScores score(FillResult fill, PositionEntity position, double recentVolatility) {
        double rm = computeReturnMagnitude(fill);
        double hpe = computeHoldPeriodEfficiency(fill, position);
        double rar = computeRiskAdjustedReturn(fill, position, recentVolatility);
        return new QualityScores(rm, hpe, rar);
    }

    double computeReturnMagnitude(FillResult fill) {
        if (fill.closedNotional() == null || fill.closedNotional().signum() == 0) {
            return 0.0;
        }
        double ratio = fill.realizedPnl()
                .divide(fill.closedNotional(), 8, RoundingMode.HALF_UP)
                .doubleValue();
        return Math.max(0.0, Math.min(1.0, ratio));
    }

    double computeHoldPeriodEfficiency(FillResult fill, PositionEntity position) {
        if (position.getOpenedAt() == null) {
            return 0.5;
        }
        long minutes = Math.max(1, Duration.between(position.getOpenedAt(), Instant.now()).toMinutes());
        double pnlPerMinute = fill.realizedPnl().doubleValue() / minutes;
        return sigmoid(pnlPerMinute, SIGMOID_SCALE_HOLD);
    }

    double computeRiskAdjustedReturn(FillResult fill, PositionEntity position, double recentVolatility) {
        if (recentVolatility < LOW_VOLATILITY_THRESHOLD) {
            return 0.5;
        }
        double positionSize = position.getQuantity().abs().doubleValue();
        if (positionSize == 0) {
            return 0.5;
        }
        double raw = fill.realizedPnl().doubleValue() / (positionSize * recentVolatility);
        return sigmoid(raw, SIGMOID_SCALE_RISK);
    }

    static double sigmoid(double x, double scale) {
        return 1.0 / (1.0 + Math.exp(-scale * x));
    }
}
