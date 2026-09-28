package io.casehub.fsitrading.spi;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "risk-assessment-engine")
public interface RiskAssessmentEngine {

    RiskResult assess(String instrument, String scenario);
}
