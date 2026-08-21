package io.casehub.fsitrading.app.incident;

import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.MarketEventType;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeChanged;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendReversalDetected;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FsiIncidentTriggerTest {

    @Test
    void flashCrash_classifiedAsCritical() {
        assertEquals(IncidentSeverity.CRITICAL,
                FsiIncidentTrigger.classifySeverity(MarketEventType.FLASH_CRASH, 14));
    }

    @Test
    void counterpartyFailure_classifiedAsCritical() {
        assertEquals(IncidentSeverity.CRITICAL,
                FsiIncidentTrigger.classifySeverity(MarketEventType.COUNTERPARTY_FAILURE, 14));
    }

    @Test
    void liquidityDrop_classifiedAsHigh() {
        assertEquals(IncidentSeverity.HIGH,
                FsiIncidentTrigger.classifySeverity(MarketEventType.LIQUIDITY_DROP, 14));
    }

    @Test
    void gapOpen_classifiedAsHigh() {
        assertEquals(IncidentSeverity.HIGH,
                FsiIncidentTrigger.classifySeverity(MarketEventType.GAP_OPEN, 14));
    }

    @Test
    void marginCall_classifiedAsHigh() {
        assertEquals(IncidentSeverity.HIGH,
                FsiIncidentTrigger.classifySeverity(MarketEventType.MARGIN_CALL, 14));
    }

    @Test
    void circuitBreaker_classifiedAsMedium() {
        assertEquals(IncidentSeverity.MEDIUM,
                FsiIncidentTrigger.classifySeverity(MarketEventType.CIRCUIT_BREAKER, 14));
    }

    @Test
    void newsEvent_classifiedAsMedium() {
        assertEquals(IncidentSeverity.MEDIUM,
                FsiIncidentTrigger.classifySeverity(MarketEventType.NEWS_EVENT, 14));
    }

    @Test
    void medium_amplifiedToHigh_beforeMarketOpen() {
        assertEquals(IncidentSeverity.HIGH,
                FsiIncidentTrigger.classifySeverity(MarketEventType.CIRCUIT_BREAKER, 5));
    }

    @Test
    void medium_amplifiedToHigh_afterMarketClose() {
        assertEquals(IncidentSeverity.HIGH,
                FsiIncidentTrigger.classifySeverity(MarketEventType.NEWS_EVENT, 21));
    }

    @Test
    void high_notAmplified_offHours() {
        assertEquals(IncidentSeverity.HIGH,
                FsiIncidentTrigger.classifySeverity(MarketEventType.LIQUIDITY_DROP, 3));
    }

    @Test
    void critical_notAmplified_offHours() {
        assertEquals(IncidentSeverity.CRITICAL,
                FsiIncidentTrigger.classifySeverity(MarketEventType.FLASH_CRASH, 3));
    }

    @Test
    void inferEventType_trendReversal_returnsFlashCrash() {
        var reversal = new TrendReversalDetected("AAPL", TrendDirection.UP, TrendDirection.DOWN, null);
        assertEquals(MarketEventType.FLASH_CRASH,
                FsiIncidentTrigger.inferEventType(reversal));
    }

    @Test
    void inferEventType_regimeToVolatile_returnsNewsEvent() {
        var change = new RegimeChanged("AAPL", MarketRegime.QUIET, MarketRegime.VOLATILE, null);
        assertEquals(MarketEventType.NEWS_EVENT,
                FsiIncidentTrigger.inferEventType(change));
    }

    @Test
    void inferEventType_regimeToMeanReverting_returnsLiquidityDrop() {
        var change = new RegimeChanged("AAPL", MarketRegime.TRENDING, MarketRegime.MEAN_REVERTING, null);
        assertEquals(MarketEventType.LIQUIDITY_DROP,
                FsiIncidentTrigger.inferEventType(change));
    }
}
