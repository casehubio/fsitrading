package io.casehub.fsitrading.app.arena;

import io.casehub.fsitrading.app.ledger.PnlAttestationService;
import io.casehub.fsitrading.app.ledger.TradingLedgerService;
import io.casehub.fsitrading.app.service.OrderService;
import io.casehub.fsitrading.app.service.PositionService;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.ApprovalOutcome;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TradeDecision;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

@ApplicationScoped
public class FsiExecutionAgent {

    private static final Logger log = Logger.getLogger(FsiExecutionAgent.class);
    private static final double DEFAULT_VOLATILITY = 0.02;

    private final OrderService orderService;
    private final PositionService positionService;
    private final StrategyService strategyService;
    private final TradingLedgerService tradingLedgerService;
    private final PnlAttestationService pnlAttestationService;
    private final io.casehub.ledger.runtime.service.federation.TrustExportService trustExportService;
    private final jakarta.enterprise.event.Event<TrustScoreChangedEvent> trustEvent;


    @Inject
    public FsiExecutionAgent(OrderService orderService,
                             PositionService positionService,
                             StrategyService strategyService,
                             TradingLedgerService tradingLedgerService,
                             PnlAttestationService pnlAttestationService,
                             io.casehub.ledger.runtime.service.federation.TrustExportService trustExportService,
                             jakarta.enterprise.event.Event<TrustScoreChangedEvent> trustEvent) {
        this.orderService          = orderService;
        this.positionService       = positionService;
        this.strategyService       = strategyService;
        this.tradingLedgerService  = tradingLedgerService;
        this.pnlAttestationService = pnlAttestationService;
        this.trustExportService    = trustExportService;
        this.trustEvent            = trustEvent;
    }

    @Transactional
    public void execute(ArenaContext ctx) {
        if (ctx.approvalOutcome() == ApprovalOutcome.REJECTED
            || ctx.approvalOutcome() == ApprovalOutcome.TIMEOUT) {
            log.infof("Arena execution skipped: approval outcome is %s", ctx.approvalOutcome());
            return;
        }

        var        consensus = ctx.consensus();
        var        signal    = ctx.marketSignal();
        BigDecimal fillPrice = signal.price();

        for (var entry : consensus.instruments().entrySet()) {
            String              instrument = entry.getKey();
            InstrumentConsensus ic         = entry.getValue();

            if (!ic.isActionable()) {
                continue;
            }

            if (!instrument.equals(signal.instrument())) {
                log.warnf("Skipping instrument %s — fill price only available for signal instrument %s",
                          instrument, signal.instrument());
                continue;
            }

            TradeDecision decision = buildConsensusDecision(instrument, ic, ctx);
            var           order    = orderService.createFromDecision(decision);
            order = orderService.fill(order.getId(), fillPrice);
            var fillResult = positionService.applyFill(order, decision.instrument().assetClass());

            var          strategy     = strategyService.findById(order.getStrategyId());
            String       strategyName = strategy != null ? strategy.getName() : "arena";
            StrategyType strategyType = strategy != null ? strategy.getStrategyType() : null;

            var evalEntryId = tradingLedgerService.recordStrategyEvaluation(
                    order.getId(), order.getStrategyId(), strategyName, strategyType,
                    instrument, ic.winningSide().name(),
                    "Arena consensus: " + ic.winningSide() + " " + ic.quantity());
            tradingLedgerService.recordOrderExecution(order, evalEntryId);

            if (fillResult.hasRealizedPnl()) {
                pnlAttestationService.recordOutcome(
                        evalEntryId, order.getId(), strategyType,
                        fillResult, DEFAULT_VOLATILITY);

                if (strategyType != null) {
                    fireTrustScoreChanged(strategyType);
                }
            }

            log.infof("Arena execution: %s %s %s @ %s (strategy: %s)",
                      ic.winningSide(), ic.quantity(), instrument, fillPrice, strategyName);
        }
    }

    private void fireTrustScoreChanged(StrategyType strategyType) {
        var    actorId   = io.casehub.fsitrading.FsiActorIdentity.forStrategy(strategyType);
        var    capTag    = io.casehub.fsitrading.FsiActorIdentity.capabilityTag(strategyType);
        double score     = 0.5;
        int    decisions = 0;
        var    trustOpt  = trustExportService.exportActor(actorId);
        if (trustOpt.isPresent()) {
            var actors = trustOpt.get().actors();
            if (!actors.isEmpty()) {
                var capScore = actors.get(0).capabilityScores().stream()
                                     .filter(c -> capTag.equals(c.capabilityTag()))
                                     .findFirst();
                if (capScore.isPresent()) {
                    score     = capScore.get().trustScore();
                    decisions = capScore.get().decisionCount();
                }
            }
        }
        String phase = decisions >= 10 ? "ACTIVE" : "BOOTSTRAP";
        trustEvent.fire(new TrustScoreChangedEvent(
                strategyType.name(), actorId, score, decisions, phase));
    }


    private TradeDecision buildConsensusDecision(String instrument,
                                                  InstrumentConsensus ic,
                                                  ArenaContext ctx) {
        var evaluations = ctx.evaluations();
        if (evaluations != null) {
            for (var eval : evaluations.values()) {
                if (eval instanceof StrategyResponse.Trade trade) {
                    for (var d : trade.decisions()) {
                        if (d.instrument().symbol().equals(instrument)
                                && d.side() == ic.winningSide()) {
                            return new TradeDecision(
                                    d.strategyId(), d.instrument(), ic.winningSide(),
                                    ic.quantity(), OrderType.MARKET, null,
                                    "Arena consensus: " + trade.rationale(), null);
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(
                "No trade decision found for instrument " + instrument
                        + " side " + ic.winningSide());
    }
}
