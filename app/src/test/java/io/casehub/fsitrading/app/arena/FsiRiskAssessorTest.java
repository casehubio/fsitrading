package io.casehub.fsitrading.app.arena;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.RiskAssessment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiRiskAssessorTest {

    private final FsiRiskAssessor assessor = new FsiRiskAssessor();

    @Test
    void deadlockedInstrument_classifiesAsHigh() {
        var consensus = new ConsensusResult(Map.of(
                "AAPL", new InstrumentConsensus(
                        InstrumentConsensus.Status.DEADLOCKED, null, null,
                        Map.of(OrderSide.BUY, 1, OrderSide.SELL, 1))));

        var assessment = assessor.assess(consensus, portfolioOf(1000));

        assertEquals(RiskAssessment.Level.HIGH, assessment.perInstrument().get("AAPL"));
        assertEquals(RiskAssessment.Level.HIGH, assessment.level());
    }

    @Test
    void noVotersInstrument_classifiesAsHigh() {
        var consensus = new ConsensusResult(Map.of(
                "AAPL", new InstrumentConsensus(
                        InstrumentConsensus.Status.NO_VOTERS, null, null, Map.of())));

        var assessment = assessor.assess(consensus, portfolioOf(1000));

        assertEquals(RiskAssessment.Level.HIGH, assessment.perInstrument().get("AAPL"));
        assertEquals(RiskAssessment.Level.HIGH, assessment.level());
    }

    @Test
    void fullLiquidation_classifiesAsCritical() {
        var consensus = consensusWithSide("AAPL", OrderSide.SELL, 500);

        var assessment = assessor.assess(consensus, positionsFor("AAPL", 500, "GOOG", 500));

        assertEquals(RiskAssessment.Level.CRITICAL, assessment.perInstrument().get("AAPL"));
        assertEquals(RiskAssessment.Level.CRITICAL, assessment.level());
    }

    @Test
    void largeTradeRelativeToPortfolio_classifiesAsHigh() {
        var consensus = consensusWithSide("AAPL", OrderSide.SELL, 300);

        var assessment = assessor.assess(consensus, portfolioOf(1000));

        assertEquals(RiskAssessment.Level.HIGH, assessment.perInstrument().get("AAPL"));
        assertEquals(RiskAssessment.Level.HIGH, assessment.level());
    }

    @Test
    void mediumTradeRelativeToPortfolio_classifiesAsMedium() {
        var consensus = consensusWithSide("AAPL", OrderSide.BUY, 150);

        var assessment = assessor.assess(consensus, portfolioOf(1000));

        assertEquals(RiskAssessment.Level.MEDIUM, assessment.perInstrument().get("AAPL"));
        assertEquals(RiskAssessment.Level.MEDIUM, assessment.level());
    }

    @Test
    void smallTrade_classifiesAsLow() {
        var consensus = consensusWithSide("AAPL", OrderSide.BUY, 10);

        var assessment = assessor.assess(consensus, portfolioOf(1000));

        assertEquals(RiskAssessment.Level.LOW, assessment.perInstrument().get("AAPL"));
        assertEquals(RiskAssessment.Level.LOW, assessment.level());
    }

    @Test
    void holdConsensus_classifiesAsLow() {
        var consensus = new ConsensusResult(Map.of(
                "AAPL", new InstrumentConsensus(
                        InstrumentConsensus.Status.CONSENSUS, null, null, Map.of())));

        var assessment = assessor.assess(consensus, portfolioOf(1000));

        assertEquals(RiskAssessment.Level.LOW, assessment.perInstrument().get("AAPL"));
    }

    @Test
    void overallLevelIsMaxPerInstrument() {
        var consensus = new ConsensusResult(Map.of(
                "AAPL", new InstrumentConsensus(
                        InstrumentConsensus.Status.CONSENSUS, OrderSide.BUY,
                        BigDecimal.valueOf(10), Map.of(OrderSide.BUY, 3)),
                "GOOG", new InstrumentConsensus(
                        InstrumentConsensus.Status.DEADLOCKED, null, null,
                        Map.of(OrderSide.BUY, 1, OrderSide.SELL, 1))));

        var assessment = assessor.assess(consensus, portfolioOf(1000));

        assertEquals(RiskAssessment.Level.LOW, assessment.perInstrument().get("AAPL"));
        assertEquals(RiskAssessment.Level.HIGH, assessment.perInstrument().get("GOOG"));
        assertEquals(RiskAssessment.Level.HIGH, assessment.level());
    }

    @Test
    void emptyPortfolio_classifiesAsLow() {
        var consensus = consensusWithSide("AAPL", OrderSide.BUY, 50);

        var assessment = assessor.assess(consensus, List.of());

        assertEquals(RiskAssessment.Level.LOW, assessment.perInstrument().get("AAPL"));
    }

    // --- helpers ---

    private ConsensusResult consensusWithSide(String instrument, OrderSide side, int quantity) {
        return new ConsensusResult(Map.of(
                instrument, new InstrumentConsensus(
                        InstrumentConsensus.Status.CONSENSUS, side,
                        BigDecimal.valueOf(quantity),
                        Map.of(side, 3))));
    }

    private List<PositionEntity> portfolioOf(int totalQuantity) {
        var pos = new PositionEntity(UUID.randomUUID(), "PORTFOLIO", AssetClass.EQUITY, UUID.randomUUID());
        pos.setQuantity(BigDecimal.valueOf(totalQuantity));
        return List.of(pos);
    }

    private List<PositionEntity> positionsFor(String inst1, int qty1, String inst2, int qty2) {
        var p1 = new PositionEntity(UUID.randomUUID(), inst1, AssetClass.EQUITY, UUID.randomUUID());
        p1.setQuantity(BigDecimal.valueOf(qty1));
        var p2 = new PositionEntity(UUID.randomUUID(), inst2, AssetClass.EQUITY, UUID.randomUUID());
        p2.setQuantity(BigDecimal.valueOf(qty2));
        return List.of(p1, p2);
    }
}
