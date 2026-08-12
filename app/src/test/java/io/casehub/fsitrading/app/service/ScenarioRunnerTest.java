package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.ScenarioType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioRunnerTest {

    private ScenarioRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ScenarioRunner();
    }

    @ParameterizedTest
    @EnumSource(ScenarioType.class)
    void allScenariosProduceTicks(ScenarioType scenario) {
        List<PriceTick> ticks = runner.generate(scenario);
        assertFalse(ticks.isEmpty(), "Scenario " + scenario + " should produce ticks");
    }

    @Test
    void normalDay_produces200Ticks() {
        List<PriceTick> ticks = runner.generate(ScenarioType.NORMAL_DAY);
        assertEquals(200, ticks.size());
    }

    @Test
    void flashCrash_produces50TicksWithPriceDrop() {
        List<PriceTick> ticks = runner.generate(ScenarioType.FLASH_CRASH);
        assertEquals(50, ticks.size());
        var firstPrice = ticks.get(0).price();
        var lastPrice = ticks.get(ticks.size() - 1).price();
        assertTrue(lastPrice.compareTo(firstPrice) < 0,
                "Flash crash should end lower than it started");
    }

    @Test
    void liquidityDrop_produces100TicksWithDecreasingVolume() {
        List<PriceTick> ticks = runner.generate(ScenarioType.LIQUIDITY_DROP);
        assertEquals(100, ticks.size());
        var earlyVolume = ticks.get(0).volume();
        var lateVolume = ticks.get(ticks.size() - 1).volume();
        assertTrue(lateVolume.compareTo(earlyVolume) < 0,
                "Liquidity drop should reduce volume over time");
    }

    @Test
    void gapOpen_produces30Ticks() {
        List<PriceTick> ticks = runner.generate(ScenarioType.GAP_OPEN);
        assertEquals(30, ticks.size());
    }

    @Test
    void multiInstrument_produces500TicksAcross25Instruments() {
        List<PriceTick> ticks = runner.generate(ScenarioType.MULTI_INSTRUMENT);
        assertEquals(500, ticks.size());
        long distinctInstruments = ticks.stream()
                .map(PriceTick::instrument)
                .distinct()
                .count();
        assertEquals(25, distinctInstruments);
    }

    @Test
    void normalDay_hasUShapedVolumeProfile() {
        List<PriceTick> ticks = runner.generate(ScenarioType.NORMAL_DAY);
        BigDecimal earlyAvg = averageVolume(ticks.subList(0, 40));
        BigDecimal midAvg = averageVolume(ticks.subList(80, 120));
        BigDecimal lateAvg = averageVolume(ticks.subList(160, 200));

        assertTrue(earlyAvg.compareTo(midAvg) > 0,
                "Early volume should exceed mid volume (U-shape)");
        assertTrue(lateAvg.compareTo(midAvg) > 0,
                "Late volume should exceed mid volume (U-shape)");
    }

    private BigDecimal averageVolume(List<PriceTick> ticks) {
        return ticks.stream()
                .map(PriceTick::volume)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(ticks.size()), java.math.RoundingMode.HALF_UP);
    }
}
