package io.casehub.fsitrading.app.arena;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.RiskAssessment;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiRiskAssessor {

    private static final double HIGH_THRESHOLD = 0.25;
    private static final double MEDIUM_THRESHOLD = 0.10;

    public RiskAssessment assess(ConsensusResult consensus, List<PositionEntity> positions) {
        BigDecimal totalPortfolioQty = positions.stream()
                .map(p -> p.getQuantity().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, RiskAssessment.Level> perInstrument = new LinkedHashMap<>();

        for (var entry : consensus.instruments().entrySet()) {
            String instrument = entry.getKey();
            InstrumentConsensus ic = entry.getValue();
            perInstrument.put(instrument,
                    classifyInstrument(ic, instrument, positions, totalPortfolioQty));
        }

        RiskAssessment.Level overall = perInstrument.values().stream()
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(RiskAssessment.Level.LOW);

        return new RiskAssessment(overall, perInstrument);
    }

    private RiskAssessment.Level classifyInstrument(InstrumentConsensus ic,
                                                     String instrument,
                                                     List<PositionEntity> positions,
                                                     BigDecimal totalPortfolioQty) {
        if (ic.isDeadlocked() || ic.hasNoVoters()) {
            return RiskAssessment.Level.HIGH;
        }

        if (!ic.isActionable()) {
            return RiskAssessment.Level.LOW;
        }

        BigDecimal tradeQty = ic.quantity();
        if (tradeQty == null || tradeQty.signum() == 0) {
            return RiskAssessment.Level.LOW;
        }

        if (ic.winningSide() == OrderSide.SELL) {
            BigDecimal instrumentQty = positions.stream()
                    .filter(p -> instrument.equals(p.getInstrument()))
                    .map(PositionEntity::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (instrumentQty.signum() > 0 && tradeQty.compareTo(instrumentQty) >= 0) {
                return RiskAssessment.Level.CRITICAL;
            }
        }

        if (totalPortfolioQty.signum() == 0) {
            return RiskAssessment.Level.LOW;
        }

        double ratio = tradeQty.doubleValue() / totalPortfolioQty.doubleValue();

        if (ratio > HIGH_THRESHOLD) return RiskAssessment.Level.HIGH;
        if (ratio > MEDIUM_THRESHOLD) return RiskAssessment.Level.MEDIUM;
        return RiskAssessment.Level.LOW;
    }
}
