package io.casehub.fsitrading.app.view;

import io.casehub.fsitrading.app.model.OhlcvBarEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OhlcvBarView(UUID id, String instrument,
                           BigDecimal openPrice, BigDecimal highPrice,
                           BigDecimal lowPrice, BigDecimal closePrice,
                           BigDecimal volume, int tickCount,
                           Instant windowStart, Instant windowEnd) {

    public static OhlcvBarView toView(OhlcvBarEntity e) {
        return new OhlcvBarView(e.getId(), e.getInstrument(),
                e.getOpenPrice(), e.getHighPrice(),
                e.getLowPrice(), e.getClosePrice(),
                e.getVolume(), e.getTickCount(),
                e.getWindowStart(), e.getWindowEnd());
    }
}
