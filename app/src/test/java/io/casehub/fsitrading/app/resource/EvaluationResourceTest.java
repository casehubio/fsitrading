package io.casehub.fsitrading.app.resource;

import io.casehub.blocks.agentic.model.ExecutionBackend;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.fsitrading.app.arena.ArenaContext;
import io.casehub.fsitrading.app.model.ArenaRunEntity;
import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.RiskAssessment;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EvaluationResourceTest {

    private EvaluationResource resource;
    private ExecutionModel<ArenaContext> arenaModel;
    private ArenaRunRepository runRepository;
    private CaseMemoryStore memoryStore;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        resource = new EvaluationResource();
        arenaModel = mock(ExecutionModel.class);
        runRepository = mock(ArenaRunRepository.class);
        memoryStore = mock(CaseMemoryStore.class);

        resource.arenaModel = arenaModel;
        resource.runRepository = runRepository;
        resource.memoryStore = memoryStore;

        ExecutionBackend<ArenaContext> backend = mock(ExecutionBackend.class);
        when(arenaModel.backend()).thenReturn(backend);
        when(backend.execute(any(), any())).thenAnswer(inv -> {
            ArenaContext ctx = inv.getArgument(1);
            ctx.setConsensus(new ConsensusResult(Map.of(
                    "AAPL", new InstrumentConsensus(
                            InstrumentConsensus.Status.CONSENSUS, OrderSide.BUY,
                            BigDecimal.valueOf(40), Map.of(OrderSide.BUY, 3)))));
            ctx.setRiskAssessment(new RiskAssessment(RiskAssessment.Level.LOW, Map.of()));
            return Uni.createFrom().item(new ExecutionResult.Completed(null));
        });
    }

    @Test
    void triggerArena_returnsResult() {
        var request = new EvaluationResource.TriggerRequest(
                "AAPL", "PRICE_MOVEMENT", new BigDecimal("185.50"), new BigDecimal("10000"));

        try (var response = resource.trigger(request, null)) {
            assertEquals(200, response.getStatus());
            var result = (ArenaResult) response.getEntity();
            assertNotNull(result.runId());
            assertNotNull(result.consensus());
        }
    }

    @Test
    void idempotentTrigger_existingCompleted_returnsExisting() {
        var existingRun = new ArenaRunEntity("AAPL");
        existingRun.complete("{\"runId\":\"test\"}");
        when(runRepository.findByIdempotencyKey(any())).thenReturn(existingRun);

        var request = new EvaluationResource.TriggerRequest(
                "AAPL", "PRICE_MOVEMENT", new BigDecimal("185.50"), null);

        try (var response = resource.trigger(request, java.util.UUID.randomUUID())) {
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void idempotentTrigger_inFlight_returns409() {
        var inFlightRun = new ArenaRunEntity("AAPL");
        when(runRepository.findByIdempotencyKey(any())).thenReturn(inFlightRun);

        var request = new EvaluationResource.TriggerRequest(
                "AAPL", "PRICE_MOVEMENT", new BigDecimal("185.50"), null);

        try (var response = resource.trigger(request, java.util.UUID.randomUUID())) {
            assertEquals(409, response.getStatus());
        }
    }
}
