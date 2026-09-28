package io.casehub.fsitrading.app.spi;

import io.casehub.fsitrading.app.arena.FsiRiskAssessor;
import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.app.service.PositionService;
import io.casehub.fsitrading.model.RiskAssessment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskAssessmentEngineImplTest {

    private FsiRiskAssessor riskAssessor;
    private PositionService positionService;
    private RiskAssessmentEngineImpl engine;

    @BeforeEach
    void setUp() {
        riskAssessor = mock(FsiRiskAssessor.class);
        positionService = mock(PositionService.class);
        engine = new RiskAssessmentEngineImpl(riskAssessor, positionService);
    }

    @Test
    void buildsSingleInstrumentConsensusAndDelegates() {
        when(positionService.findAll()).thenReturn(List.of());
        when(riskAssessor.assess(any(), any())).thenReturn(
                new RiskAssessment(RiskAssessment.Level.LOW, Map.of("AAPL", RiskAssessment.Level.LOW)));

        var result = engine.assess("AAPL", "normal");

        assertThat(result.level()).isEqualTo("LOW");
        var consensusCaptor = ArgumentCaptor.forClass(io.casehub.fsitrading.model.ConsensusResult.class);
        verify(riskAssessor).assess(consensusCaptor.capture(), any());
        assertThat(consensusCaptor.getValue().instruments()).containsKey("AAPL");
    }

    @Test
    void mapsHighRiskLevel() {
        when(positionService.findAll()).thenReturn(List.of());
        when(riskAssessor.assess(any(), any())).thenReturn(
                new RiskAssessment(RiskAssessment.Level.HIGH, Map.of()));

        var result = engine.assess("SPY", "volatile");

        assertThat(result.level()).isEqualTo("HIGH");
    }

    @Test
    void passesPositionsToAssessor() {
        var position = mock(PositionEntity.class);
        when(positionService.findAll()).thenReturn(List.of(position));
        when(riskAssessor.assess(any(), any())).thenReturn(
                new RiskAssessment(RiskAssessment.Level.MEDIUM, Map.of()));

        engine.assess("AAPL", "normal");

        verify(riskAssessor).assess(any(), org.mockito.ArgumentMatchers.eq(List.of(position)));
    }
}
