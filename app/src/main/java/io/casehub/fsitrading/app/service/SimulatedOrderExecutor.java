package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.ledger.PnlAttestationService;
import io.casehub.fsitrading.app.ledger.TradingLedgerService;
import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TradeDecision;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

@ApplicationScoped
public class SimulatedOrderExecutor {

    private static final Logger log = Logger.getLogger(SimulatedOrderExecutor.class);

    @Inject
    OrderService orderService;

    @Inject
    PositionService positionService;

    @Inject
    StrategyService strategyService;

    @Inject
    TradingLedgerService tradingLedgerService;

    @Inject
    PnlAttestationService pnlAttestationService;

    public OrderEntity executeDecision(TradeDecision decision, MarketEventEntity triggeringEvent) {
        var order = orderService.createFromDecision(decision);
        var fillPrice = determineFillPrice(decision, triggeringEvent);
        order = orderService.fill(order.getId(), fillPrice);
        var fillResult = positionService.applyFill(order, decision.instrument().assetClass());

        var strategy = strategyService.findById(order.getStrategyId());
        if (strategy == null) {
            throw new IllegalStateException(
                    "Strategy not found for id " + order.getStrategyId() + " — cannot derive actor identity for trust scoring");
        }
        var strategyName = strategy.getName();
        var strategyType = strategy.getStrategyType();
        var evalEntryId = tradingLedgerService.recordStrategyEvaluation(
                order.getId(), order.getStrategyId(), strategyName, strategyType,
                decision.instrument().symbol(), decision.side().name(), decision.rationale());
        tradingLedgerService.recordOrderExecution(order, evalEntryId);

        if (fillResult.hasRealizedPnl()) {
            pnlAttestationService.recordOutcome(evalEntryId, order.getId(),
                    strategyType, fillResult);
        }

        log.infof("Simulated fill: %s %s %s @ %s (strategy: %s, ledger: eval=%s, pnl=%s)",
                decision.side(), decision.quantity(), decision.instrument().symbol(),
                fillPrice, decision.strategyId(), evalEntryId,
                fillResult.hasRealizedPnl() ? fillResult.realizedPnl() : "none");
        return order;
    }

    private BigDecimal determineFillPrice(TradeDecision decision, MarketEventEntity event) {
        if (decision.limitPrice() != null) {
            return decision.limitPrice();
        }
        return event.getPrice();
    }
}
