package io.casehub.fsitrading.app.push;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface TradingPushPayload {

    String type();

    record PositionUpdate(
            String type,
            UUID positionId,
            String instrument,
            String assetClass,
            UUID strategyId,
            BigDecimal quantity,
            BigDecimal avgCost,
            BigDecimal realizedPnl,
            Instant updatedAt) implements TradingPushPayload {
        public PositionUpdate(UUID positionId, String instrument, String assetClass,
                              UUID strategyId, BigDecimal quantity, BigDecimal avgCost,
                              BigDecimal realizedPnl, Instant updatedAt) {
            this("POSITION_UPDATE", positionId, instrument, assetClass,
                    strategyId, quantity, avgCost, realizedPnl, updatedAt);
        }
    }

    record PnlUpdate(
            String type,
            UUID strategyId,
            String instrument,
            BigDecimal realizedPnl,
            BigDecimal fillPrice,
            BigDecimal closedQuantity,
            Instant updatedAt) implements TradingPushPayload {
        public PnlUpdate(UUID strategyId, String instrument, BigDecimal realizedPnl,
                         BigDecimal fillPrice, BigDecimal closedQuantity, Instant updatedAt) {
            this("PNL_UPDATE", strategyId, instrument, realizedPnl,
                    fillPrice, closedQuantity, updatedAt);
        }
    }

    record TrustUpdate(
            String type,
            String strategyType,
            String actorId,
            double trustScore,
            int decisionCount,
            String phase) implements TradingPushPayload {
        public TrustUpdate(String strategyType, String actorId, double trustScore,
                           int decisionCount, String phase) {
            this("TRUST_UPDATE", strategyType, actorId, trustScore,
                    decisionCount, phase);
        }
    }

    record RoutingUpdate(
            String type,
            UUID evaluationId,
            String instrument,
            List<String> selectedAgents,
            String routingStrategy,
            Instant decidedAt) implements TradingPushPayload {
        public RoutingUpdate(UUID evaluationId, String instrument,
                             List<String> selectedAgents, String routingStrategy,
                             Instant decidedAt) {
            this("ROUTING_UPDATE", evaluationId, instrument, selectedAgents,
                    routingStrategy, decidedAt);
        }
    }
}
