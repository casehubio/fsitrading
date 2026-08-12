package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public class FsiTrendSummariser implements Summariser.SyncSummariser<OHLCV, TrendSummary> {

    private static final double SIDEWAYS_THRESHOLD = 0.005;

    @Override
    public List<TrendSummary> summarise(List<LevelEvent<OHLCV>> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }

        var bars = batch.stream().map(LevelEvent::payload).toList();
        String instrument = bars.get(0).instrument();

        double momentum = computeMomentum(bars);
        double volatility = computeVolatility(bars);
        String volumeProfile = classifyVolumeProfile(bars);
        TrendDirection direction = classifyDirection(momentum);

        Instant windowStart = bars.get(0).windowStart();
        Instant windowEnd = bars.get(bars.size() - 1).windowEnd();

        return List.of(new TrendSummary(instrument, direction, momentum,
                volatility, volumeProfile, windowStart, windowEnd));
    }

    private double computeMomentum(List<OHLCV> bars) {
        if (bars.size() < 2) return 0.0;
        double firstClose = bars.get(0).close().doubleValue();
        double lastClose = bars.get(bars.size() - 1).close().doubleValue();
        if (firstClose == 0.0) return 0.0;
        return (lastClose - firstClose) / firstClose;
    }

    private double computeVolatility(List<OHLCV> bars) {
        if (bars.size() < 2) return 0.0;
        double[] closes = bars.stream()
                .mapToDouble(b -> b.close().doubleValue())
                .toArray();
        double mean = 0;
        for (double c : closes) mean += c;
        mean /= closes.length;

        double variance = 0;
        for (double c : closes) {
            double diff = c - mean;
            variance += diff * diff;
        }
        variance /= closes.length;
        return Math.sqrt(variance) / mean;
    }

    private String classifyVolumeProfile(List<OHLCV> bars) {
        if (bars.size() < 3) return "FLAT";
        BigDecimal totalVolume = bars.stream()
                .map(OHLCV::volume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgVolume = totalVolume.divide(
                BigDecimal.valueOf(bars.size()), RoundingMode.HALF_UP);
        BigDecimal lastVolume = bars.get(bars.size() - 1).volume();

        if (lastVolume.compareTo(avgVolume.multiply(BigDecimal.valueOf(1.5))) > 0) {
            return "INCREASING";
        } else if (lastVolume.compareTo(avgVolume.multiply(BigDecimal.valueOf(0.5))) < 0) {
            return "DECREASING";
        }
        return "FLAT";
    }

    private TrendDirection classifyDirection(double momentum) {
        if (momentum > SIDEWAYS_THRESHOLD) return TrendDirection.UP;
        if (momentum < -SIDEWAYS_THRESHOLD) return TrendDirection.DOWN;
        return TrendDirection.SIDEWAYS;
    }
}
