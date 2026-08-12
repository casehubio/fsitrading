package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class FsiOhlcvSummariser implements Summariser.SyncSummariser<PriceTick, OHLCV> {

    @Override
    public List<OHLCV> summarise(List<LevelEvent<PriceTick>> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }

        var ticks = batch.stream().map(LevelEvent::payload).toList();
        String instrument = ticks.get(0).instrument();

        BigDecimal open = ticks.get(0).price();
        BigDecimal close = ticks.get(ticks.size() - 1).price();
        BigDecimal high = ticks.stream().map(PriceTick::price).reduce(BigDecimal::max).orElse(open);
        BigDecimal low = ticks.stream().map(PriceTick::price).reduce(BigDecimal::min).orElse(open);
        BigDecimal volume = ticks.stream().map(PriceTick::volume).reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant windowStart = ticks.get(0).timestamp();
        Instant windowEnd = ticks.get(ticks.size() - 1).timestamp();

        return List.of(new OHLCV(instrument, open, high, low, close, volume,
                ticks.size(), windowStart, windowEnd));
    }
}
