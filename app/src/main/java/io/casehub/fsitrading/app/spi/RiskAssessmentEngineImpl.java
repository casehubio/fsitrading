package io.casehub.fsitrading.app.spi;

import io.casehub.fsitrading.app.arena.FsiRiskAssessor;
import io.casehub.fsitrading.app.service.PositionService;
import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.spi.RiskAssessmentEngine;
import io.casehub.fsitrading.spi.RiskResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.Map;

@ApplicationScoped
public class RiskAssessmentEngineImpl implements RiskAssessmentEngine {

    private final FsiRiskAssessor riskAssessor;
    private final PositionService positionService;

    @Inject
    public RiskAssessmentEngineImpl(FsiRiskAssessor riskAssessor, PositionService positionService) {
        this.riskAssessor = riskAssessor;
        this.positionService = positionService;
    }

    @Override
    public RiskResult assess(String instrument, String scenario) {
        var consensus = new ConsensusResult(Map.of(instrument,
                new InstrumentConsensus(InstrumentConsensus.Status.CONSENSUS,
                        OrderSide.BUY, BigDecimal.ONE, Map.of(OrderSide.BUY, 1))));

        var positions = positionService.findAll();
        var assessment = riskAssessor.assess(consensus, positions);
        var instrumentLevel = assessment.perInstrument().getOrDefault(instrument, assessment.level());

        return new RiskResult(instrumentLevel.name(), BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
