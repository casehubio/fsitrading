package io.casehub.fsitrading.app.arena;

import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.fsitrading.app.service.PositionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ArenaConfigurationTest {

    @Test
    void producesExecutionModel() {
        var config = new ArenaConfiguration(
                mock(FsiArenaRouting.class),
                mock(FsiMajorityVoteByInstrument.class),
                mock(FsiRiskAssessor.class),
                mock(FsiRiskGateRouting.class),
                mock(FsiExecutionAgent.class),
                mock(PositionService.class),
                List.of());

        var model = config.arenaModel();

        assertNotNull(model);
        assertEquals(PatternType.SEQUENCE, model.patternType());
        assertEquals("strategy-arena", model.task());
    }

    @Test
    void modelHasSixSteps() {
        var config = new ArenaConfiguration(
                mock(FsiArenaRouting.class),
                mock(FsiMajorityVoteByInstrument.class),
                mock(FsiRiskAssessor.class),
                mock(FsiRiskGateRouting.class),
                mock(FsiExecutionAgent.class),
                mock(PositionService.class),
                List.of());

        var model = config.arenaModel();
        var candidates = model.candidateSupplier().get();

        assertEquals(6, candidates.size());
    }
}
