package io.casehub.fsitrading.app.arena;

import io.casehub.fsitrading.app.ledger.PnlAttestationService;
import io.casehub.fsitrading.app.ledger.TradingLedgerService;
import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.app.service.FillResult;
import io.casehub.fsitrading.app.service.OrderService;
import io.casehub.fsitrading.app.service.PositionService;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.ApprovalOutcome;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.Instrument;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TradeDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FsiExecutionAgentTest {

    private static final MarketSignal SIGNAL = new MarketSignal(
            "AAPL", "PRICE_MOVEMENT", new BigDecimal("185.50"),
            new BigDecimal("10000"), Instant.now());
    private static final UUID STRATEGY_ID = UUID.randomUUID();
    private static final Instrument AAPL = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");

    private OrderService orderService;
    private PositionService positionService;
    private TradingLedgerService tradingLedgerService;
    private PnlAttestationService pnlAttestationService;
    private StrategyService strategyService;
    private FsiExecutionAgent executionAgent;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        positionService = mock(PositionService.class);
        tradingLedgerService = mock(TradingLedgerService.class);
        pnlAttestationService = mock(PnlAttestationService.class);
        strategyService = mock(StrategyService.class);
        executionAgent = new FsiExecutionAgent(
                orderService, positionService, strategyService,
                tradingLedgerService, pnlAttestationService);

        var order = mock(OrderEntity.class);
        when(order.getId()).thenReturn(UUID.randomUUID());
        when(order.getStrategyId()).thenReturn(STRATEGY_ID);
        when(orderService.createFromDecision(any())).thenReturn(order);
        when(orderService.fill(any(), any())).thenReturn(order);
        when(positionService.applyFill(any(), any())).thenReturn(
                new FillResult(null, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("185.50"), BigDecimal.ZERO));
        when(tradingLedgerService.recordStrategyEvaluation(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());
    }

    @Test
    void executesConsensusDecision_createsOrder() {
        var context = arenaContextWithConsensus(
                buy("AAPL", 40), ApprovalOutcome.NOT_REQUIRED);

        executionAgent.execute(context);

        verify(orderService).createFromDecision(any());
        verify(orderService).fill(any(), any());
        verify(positionService).applyFill(any(), any());
        verify(tradingLedgerService).recordStrategyEvaluation(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectedApproval_skipsExecution() {
        var context = arenaContextWithConsensus(
                buy("AAPL", 40), ApprovalOutcome.REJECTED);

        executionAgent.execute(context);

        verify(orderService, never()).createFromDecision(any());
    }

    @Test
    void timeoutApproval_skipsExecution() {
        var context = arenaContextWithConsensus(
                buy("AAPL", 40), ApprovalOutcome.TIMEOUT);

        executionAgent.execute(context);

        verify(orderService, never()).createFromDecision(any());
    }

    @Test
    void nonActionableConsensus_skipsExecution() {
        var holdConsensus = new ConsensusResult(Map.of(
                "AAPL", new InstrumentConsensus(
                        InstrumentConsensus.Status.CONSENSUS, null, null, Map.of())));
        var context = new ArenaContext(SIGNAL);
        context.setConsensus(holdConsensus);
        context.setEvaluations(Map.of());
        context.setApprovalOutcome(ApprovalOutcome.NOT_REQUIRED);

        executionAgent.execute(context);

        verify(orderService, never()).createFromDecision(any());
    }

    // --- helpers ---

    private ArenaContext arenaContextWithConsensus(ConsensusResult consensus,
                                                    ApprovalOutcome outcome) {
        var ctx = new ArenaContext(SIGNAL);
        ctx.setConsensus(consensus);
        ctx.setApprovalOutcome(outcome);
        ctx.setEvaluations(Map.of(
                StrategyType.MOMENTUM, new StrategyResponse.Trade(
                        List.of(new TradeDecision(
                                STRATEGY_ID.toString(), AAPL, OrderSide.BUY,
                                BigDecimal.valueOf(40), OrderType.MARKET, null,
                                "momentum detected", null)),
                        "momentum signal")));
        return ctx;
    }

    private ConsensusResult buy(String instrument, int quantity) {
        return new ConsensusResult(Map.of(
                instrument, new InstrumentConsensus(
                        InstrumentConsensus.Status.CONSENSUS, OrderSide.BUY,
                        BigDecimal.valueOf(quantity),
                        Map.of(OrderSide.BUY, 3))));
    }
}
