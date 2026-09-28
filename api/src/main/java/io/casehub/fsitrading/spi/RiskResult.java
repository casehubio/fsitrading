package io.casehub.fsitrading.spi;

import java.math.BigDecimal;

public record RiskResult(String level, BigDecimal varAmount, BigDecimal exposurePct) {}
