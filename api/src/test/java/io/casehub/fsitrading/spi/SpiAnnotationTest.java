package io.casehub.fsitrading.spi;

import io.casehub.platform.simulation.SimulationEligible;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiAnnotationTest {

    @Test
    void allSpiInterfacesAreSimulationEligible() {
        List<Class<?>> spis = List.of(
                MarketDataProvider.class,
                OrderExecutionService.class,
                RiskAssessmentEngine.class,
                StrategyEvaluationService.class);

        for (var spi : spis) {
            assertTrue(spi.isInterface(), spi.getSimpleName() + " should be an interface");
            SimulationEligible ann = spi.getAnnotation(SimulationEligible.class);
            assertNotNull(ann, spi.getSimpleName() + " should have @SimulationEligible");
            assertTrue(ann.name().length() > 0, spi.getSimpleName() + " @SimulationEligible.name() should be non-empty");
        }
    }
}
