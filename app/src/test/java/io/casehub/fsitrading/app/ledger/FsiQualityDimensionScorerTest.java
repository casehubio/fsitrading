package io.casehub.fsitrading.app.ledger;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.FillResult;
import io.casehub.fsitrading.model.AssetClass;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiQualityDimensionScorerTest {

    private final FsiQualityDimensionScorer scorer = new FsiQualityDimensionScorer();

    @Test
    void returnMagnitude_profitableTrade() {
        var scores = scorer.score(fill("100", "1000"), positionOpenedMinutesAgo(30), 0.02);
        assertEquals(0.1, scores.returnMagnitude(), 0.001);
    }

    @Test
    void returnMagnitude_lossClampsToZero() {
        var scores = scorer.score(fill("-50", "1000"), positionOpenedMinutesAgo(30), 0.02);
        assertEquals(0.0, scores.returnMagnitude(), 0.001);
    }

    @Test
    void returnMagnitude_largeProfitCapsAtOne() {
        var scores = scorer.score(fill("2000", "1000"), positionOpenedMinutesAgo(30), 0.02);
        assertEquals(1.0, scores.returnMagnitude(), 0.001);
    }

    @Test
    void returnMagnitude_zeroNotionalReturnsZero() {
        var scores = scorer.score(fill("100", "0"), positionOpenedMinutesAgo(30), 0.02);
        assertEquals(0.0, scores.returnMagnitude(), 0.001);
    }

    @Test
    void holdPeriodEfficiency_profitableAboveHalf() {
        var scores = scorer.score(fill("100", "1000"), positionOpenedMinutesAgo(60), 0.02);
        assertTrue(scores.holdPeriodEfficiency() > 0.5, "Profitable trade should have efficiency > 0.5");
        assertTrue(scores.holdPeriodEfficiency() <= 1.0);
    }

    @Test
    void holdPeriodEfficiency_lossBelow() {
        var scores = scorer.score(fill("-100", "1000"), positionOpenedMinutesAgo(60), 0.02);
        assertTrue(scores.holdPeriodEfficiency() < 0.5, "Loss should have efficiency < 0.5");
        assertTrue(scores.holdPeriodEfficiency() >= 0.0);
    }

    @Test
    void holdPeriodEfficiency_subMinuteClampsToOne() {
        var scores = scorer.score(fill("10", "100"), positionOpenedSecondsAgo(5), 0.02);
        assertTrue(scores.holdPeriodEfficiency() > 0.5);
    }

    @Test
    void holdPeriodEfficiency_nullOpenedAtDefaultsToHalf() {
        var scores = scorer.score(fill("100", "1000"), positionWithNullOpenedAt(), 0.02);
        assertEquals(0.5, scores.holdPeriodEfficiency(), 0.001);
    }

    @Test
    void riskAdjustedReturn_lowVolatilityDefaultsToHalf() {
        var scores = scorer.score(fill("50", "500"), positionOpenedMinutesAgo(30), 0.00001);
        assertEquals(0.5, scores.riskAdjustedReturn(), 0.001);
    }

    @Test
    void riskAdjustedReturn_profitableWithNormalVolatility() {
        var scores = scorer.score(fill("100", "1000"), positionOpenedMinutesAgo(30), 0.02);
        assertTrue(scores.riskAdjustedReturn() > 0.5, "Profitable risk-adjusted return should be > 0.5");
    }

    @Test
    void riskAdjustedReturn_lossWithNormalVolatility() {
        var scores = scorer.score(fill("-100", "1000"), positionOpenedMinutesAgo(30), 0.02);
        assertTrue(scores.riskAdjustedReturn() < 0.5, "Loss risk-adjusted return should be < 0.5");
    }

    @Test
    void allScoresInZeroOneRange() {
        var scores = scorer.score(fill("500", "2000"), positionOpenedMinutesAgo(120), 0.05);
        assertTrue(scores.returnMagnitude() >= 0.0 && scores.returnMagnitude() <= 1.0);
        assertTrue(scores.holdPeriodEfficiency() >= 0.0 && scores.holdPeriodEfficiency() <= 1.0);
        assertTrue(scores.riskAdjustedReturn() >= 0.0 && scores.riskAdjustedReturn() <= 1.0);
    }

    private FillResult fill(String pnl, String notional) {
        var position = new PositionEntity(UUID.randomUUID(), "AAPL", AssetClass.EQUITY, UUID.randomUUID());
        position.setQuantity(BigDecimal.valueOf(100));
        return new FillResult(position, new BigDecimal(pnl), new BigDecimal(notional),
                BigDecimal.valueOf(185), BigDecimal.valueOf(50));
    }

    private PositionEntity positionOpenedMinutesAgo(long minutes) {
        var position = new PositionEntity(UUID.randomUUID(), "AAPL", AssetClass.EQUITY, UUID.randomUUID());
        position.setQuantity(BigDecimal.valueOf(100));
        position.setOpenedAt(Instant.now().minus(minutes, ChronoUnit.MINUTES));
        return position;
    }

    private PositionEntity positionOpenedSecondsAgo(long seconds) {
        var position = new PositionEntity(UUID.randomUUID(), "AAPL", AssetClass.EQUITY, UUID.randomUUID());
        position.setQuantity(BigDecimal.valueOf(100));
        position.setOpenedAt(Instant.now().minus(seconds, ChronoUnit.SECONDS));
        return position;
    }

    private PositionEntity positionWithNullOpenedAt() {
        var position = new PositionEntity(UUID.randomUUID(), "AAPL", AssetClass.EQUITY, UUID.randomUUID());
        position.setQuantity(BigDecimal.valueOf(100));
        return position;
    }
}
