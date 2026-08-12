package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.ScenarioType;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class ScenarioRunner {

    private static final List<ScenarioInstrument> BASE_INSTRUMENTS = List.of(
            new ScenarioInstrument("AAPL", 175.00),
            new ScenarioInstrument("MSFT", 420.00),
            new ScenarioInstrument("GOOGL", 175.00),
            new ScenarioInstrument("AMZN", 185.00),
            new ScenarioInstrument("NVDA", 130.00));

    public List<PriceTick> generate(ScenarioType scenario) {
        return switch (scenario) {
            case NORMAL_DAY -> generateNormalDay();
            case FLASH_CRASH -> generateFlashCrash();
            case LIQUIDITY_DROP -> generateLiquidityDrop();
            case GAP_OPEN -> generateGapOpen();
            case MULTI_INSTRUMENT -> generateMultiInstrument();
        };
    }

    private List<PriceTick> generateNormalDay() {
        var ticks = new ArrayList<PriceTick>();
        var random = ThreadLocalRandom.current();
        var baseTime = Instant.now();

        for (int i = 0; i < 200; i++) {
            var instr = BASE_INSTRUMENTS.get(i % BASE_INSTRUMENTS.size());
            double fraction = (double) i / 200;
            double volumeMultiplier = 1.0 + Math.abs(2.0 * fraction - 1.0);
            double drift = (random.nextDouble() - 0.5) * 0.02;
            double price = instr.basePrice * (1.0 + drift);
            double volume = (random.nextInt(1000, 10000)) * volumeMultiplier;

            ticks.add(new PriceTick(
                    instr.symbol,
                    BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(volume).setScale(0, RoundingMode.HALF_UP),
                    baseTime.plusMillis(i * 500L),
                    false));
        }
        return ticks;
    }

    private List<PriceTick> generateFlashCrash() {
        var ticks = new ArrayList<PriceTick>();
        var random = ThreadLocalRandom.current();
        var baseTime = Instant.now();
        var instr = BASE_INSTRUMENTS.get(0);

        for (int i = 0; i < 50; i++) {
            double dropFraction = (double) i / 50;
            double drop = -0.08 * dropFraction;
            double currentPrice = instr.basePrice * (1.0 + drop + (random.nextDouble() - 0.5) * 0.005);
            double volume = random.nextInt(10000, 100000);

            ticks.add(new PriceTick(
                    instr.symbol,
                    BigDecimal.valueOf(currentPrice).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(volume).setScale(0, RoundingMode.HALF_UP),
                    baseTime.plusMillis(i * 600L),
                    false));
        }
        return ticks;
    }

    private List<PriceTick> generateLiquidityDrop() {
        var ticks = new ArrayList<PriceTick>();
        var random = ThreadLocalRandom.current();
        var baseTime = Instant.now();

        for (int i = 0; i < 100; i++) {
            var instr = BASE_INSTRUMENTS.get(i % BASE_INSTRUMENTS.size());
            double volumeDecay = 1.0 - 0.9 * ((double) i / 100);
            double price = instr.basePrice * (1.0 + (random.nextDouble() - 0.5) * 0.01);
            double volume = random.nextInt(1000, 10000) * volumeDecay;

            ticks.add(new PriceTick(
                    instr.symbol,
                    BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(Math.max(1, volume)).setScale(0, RoundingMode.HALF_UP),
                    baseTime.plusMillis(i * 1000L),
                    false));
        }
        return ticks;
    }

    private List<PriceTick> generateGapOpen() {
        var ticks = new ArrayList<PriceTick>();
        var random = ThreadLocalRandom.current();
        var baseTime = Instant.now();
        var instr = BASE_INSTRUMENTS.get(0);
        double gapPrice = instr.basePrice * 1.03;

        for (int i = 0; i < 30; i++) {
            double drift = (random.nextDouble() - 0.5) * 0.01;
            double price = gapPrice * (1.0 + drift);
            double volume = random.nextInt(5000, 30000);

            ticks.add(new PriceTick(
                    instr.symbol,
                    BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(volume).setScale(0, RoundingMode.HALF_UP),
                    baseTime.plusMillis(i * 500L),
                    false));
        }
        return ticks;
    }

    private List<PriceTick> generateMultiInstrument() {
        var ticks = new ArrayList<PriceTick>();
        var random = ThreadLocalRandom.current();
        var baseTime = Instant.now();

        var instruments = new ArrayList<ScenarioInstrument>();
        instruments.addAll(BASE_INSTRUMENTS);
        for (int i = 5; i < 25; i++) {
            instruments.add(new ScenarioInstrument(
                    "SYM" + String.format("%02d", i),
                    50.0 + random.nextDouble() * 200));
        }

        for (int i = 0; i < 500; i++) {
            var instr = instruments.get(i % instruments.size());
            double drift = (random.nextDouble() - 0.5) * 0.02;
            double price = instr.basePrice * (1.0 + drift);
            double volume = random.nextInt(500, 5000);

            ticks.add(new PriceTick(
                    instr.symbol,
                    BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(volume).setScale(0, RoundingMode.HALF_UP),
                    baseTime.plusMillis(i * 200L),
                    false));
        }
        return ticks;
    }

    record ScenarioInstrument(String symbol, double basePrice) {}
}
