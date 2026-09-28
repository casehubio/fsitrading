package io.casehub.fsitrading.spi;

import io.casehub.fsitrading.model.OrderSide;

import java.math.BigDecimal;

public record EvaluationResult(String action, OrderSide side, BigDecimal confidence, String rationale) {}
