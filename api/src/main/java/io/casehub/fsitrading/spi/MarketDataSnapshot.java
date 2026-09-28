package io.casehub.fsitrading.spi;

import java.math.BigDecimal;

public record MarketDataSnapshot(BigDecimal bid, BigDecimal ask, BigDecimal spread, BigDecimal volume) {}
