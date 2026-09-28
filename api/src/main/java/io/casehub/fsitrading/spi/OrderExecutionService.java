package io.casehub.fsitrading.spi;

import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderStatus;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.platform.simulation.SimulationEligible;

import java.math.BigDecimal;
import java.util.UUID;

@SimulationEligible(name = "order-execution-service")
public interface OrderExecutionService {

    OrderResult submit(String instrument, OrderSide side, BigDecimal quantity, OrderType type);

    void cancel(UUID orderId);

    OrderStatus status(UUID orderId);
}
