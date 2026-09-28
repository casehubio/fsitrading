package io.casehub.fsitrading.spi;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "strategy-evaluation-service")
public interface StrategyEvaluationService {

    EvaluationResult evaluate(String instrument, String strategy, String regime);
}
