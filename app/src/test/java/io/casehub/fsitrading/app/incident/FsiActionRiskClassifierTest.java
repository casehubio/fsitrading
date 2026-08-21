package io.casehub.fsitrading.app.incident;

import io.casehub.api.spi.ClassificationContext;
import io.casehub.api.spi.RiskDecision;
import io.casehub.worker.api.PlannedAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FsiActionRiskClassifierTest {

    private FsiActionRiskClassifier classifier;
    private ClassificationContext   ctx;

    @BeforeEach
    void setUp() {
        classifier = new FsiActionRiskClassifier();
        ctx        = new ClassificationContext("worker-1", UUID.randomUUID(),
                                               "default", "overnight-incident", "trading", "respond");
    }

    @Test
    void closeSmallPosition_autonomous() {
        var action = PlannedAction.of("Close 5% of AAPL position", "close-position",
                                      Map.of("portfolioRatio", 0.05));
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, ctx));
    }

    @Test
    void closeMediumPosition_autonomous() {
        var action = PlannedAction.of("Close 15% of portfolio", "close-position",
                                      Map.of("portfolioRatio", 0.15));
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, ctx));
    }

    @Test
    void closeLargePosition_gateRequired() {
        var action = PlannedAction.of("Close 30% of portfolio", "close-position",
                                      Map.of("portfolioRatio", 0.30));
        var decision = classifier.classify(action, ctx);
        assertInstanceOf(RiskDecision.GateRequired.class, decision);
        var gate = (RiskDecision.GateRequired) decision;
        assertEquals("fsitrading", gate.scope());
        assertFalse(gate.reversible());
        assertNotNull(gate.candidateGroups());
    }

    @Test
    void closeBoundaryPosition_25percent_autonomous() {
        var action = PlannedAction.of("Close exactly 25%", "close-position",
                                      Map.of("portfolioRatio", 0.25));
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, ctx));
    }

    @Test
    void closeJustOver25percent_gateRequired() {
        var action = PlannedAction.of("Close 25.1%", "close-position",
                                      Map.of("portfolioRatio", 0.251));
        assertInstanceOf(RiskDecision.GateRequired.class, classifier.classify(action, ctx));
    }

    @Test
    void fullLiquidation_gateRequired() {
        var action = PlannedAction.of("Full liquidation", "full-liquidation",
                                      Map.of("portfolioRatio", 1.0));
        assertInstanceOf(RiskDecision.GateRequired.class, classifier.classify(action, ctx));
    }

    @Test
    void newPositionDuringIncident_autonomous() {
        var action = PlannedAction.of("Open hedge position", "new-position",
                                      Map.of("portfolioRatio", 0.05));
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, ctx));
    }

    @Test
    void counterpartyExposureClose_gateRequired_regardlessOfSize() {
        var action = PlannedAction.of("Close counterparty exposure", "counterparty-close",
                                      Map.of("portfolioRatio", 0.02));
        assertInstanceOf(RiskDecision.GateRequired.class, classifier.classify(action, ctx));
    }

    @Test
    void unknownActionType_noPortfolioRatio_autonomous() {
        var action = PlannedAction.of("Some other action", "other", Map.of());
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, ctx));
    }

    @Test
    void missingPortfolioRatio_treatedAsZero_autonomous() {
        var action = PlannedAction.of("Close positions", "close-position", Map.of());
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, ctx));
    }
}
