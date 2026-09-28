package io.casehub.fsitrading.spi;

import io.casehub.fsitrading.model.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResult(UUID orderId, OrderStatus status, BigDecimal fillPrice) {}
