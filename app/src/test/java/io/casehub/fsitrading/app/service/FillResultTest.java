package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.model.AssetClass;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FillResultTest {

    @Test
    void hasRealizedPnl_profitReturnsTrue() {
        var result = new FillResult(dummyPosition(), BigDecimal.valueOf(100), BigDecimal.valueOf(5000),
                BigDecimal.valueOf(50), BigDecimal.TEN);
        assertTrue(result.hasRealizedPnl());
    }

    @Test
    void hasRealizedPnl_lossReturnsTrue() {
        var result = new FillResult(dummyPosition(), BigDecimal.valueOf(-50), BigDecimal.valueOf(5000),
                BigDecimal.valueOf(50), BigDecimal.TEN);
        assertTrue(result.hasRealizedPnl());
    }

    @Test
    void hasRealizedPnl_zeroReturnsFalse() {
        var result = new FillResult(dummyPosition(), BigDecimal.ZERO, BigDecimal.valueOf(5000),
                BigDecimal.valueOf(50), BigDecimal.TEN);
        assertFalse(result.hasRealizedPnl());
    }

    @Test
    void hasRealizedPnl_nullReturnsFalse() {
        var result = new FillResult(dummyPosition(), null, null, null, null);
        assertFalse(result.hasRealizedPnl());
    }

    private PositionEntity dummyPosition() {
        return new PositionEntity(UUID.randomUUID(), "AAPL", AssetClass.EQUITY, UUID.randomUUID());
    }
}
