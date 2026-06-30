package io.casehub.fsitrading.app.engine;

import io.casehub.api.model.CaseDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrategyEvaluationCaseDefinitionTest {

    @Test
    void build_producesValidDefinition() {
        CaseDefinition def = StrategyEvaluationCaseDefinition.build();

        assertEquals("fsitrading", def.getNamespace());
        assertEquals("strategy-evaluation", def.getName());
        assertEquals("1.0.0", def.getVersion());
        assertNotNull(def.getTitle());
    }

    @Test
    void build_hasThreeCapabilities() {
        CaseDefinition def = StrategyEvaluationCaseDefinition.build();

        assertEquals(3, def.getCapabilities().size());
        var names = def.getCapabilities().stream()
                .map(c -> c.name()).toList();
        assertTrue(names.contains("strategy-evaluation"));
        assertTrue(names.contains("risk-assessment"));
        assertTrue(names.contains("order-execution"));
    }

    @Test
    void build_hasFourBindings() {
        CaseDefinition def = StrategyEvaluationCaseDefinition.build();

        assertEquals(4, def.getBindings().size());
        var names = def.getBindings().stream()
                .map(b -> b.getName()).toList();
        assertTrue(names.contains("evaluate-strategy"));
        assertTrue(names.contains("assess-risk"));
        assertTrue(names.contains("human-approval-gate"));
        assertTrue(names.contains("execute-trade"));
    }

    @Test
    void build_hasFourGoals() {
        CaseDefinition def = StrategyEvaluationCaseDefinition.build();

        assertEquals(4, def.getGoals().size());
    }

    @Test
    void build_hasTwoMilestones() {
        CaseDefinition def = StrategyEvaluationCaseDefinition.build();

        assertEquals(2, def.getMilestones().size());
        var names = def.getMilestones().stream()
                .map(m -> m.getName()).toList();
        assertTrue(names.contains("strategy-evaluated"));
        assertTrue(names.contains("risk-assessed"));
    }

    @Test
    void build_hasCompletion() {
        CaseDefinition def = StrategyEvaluationCaseDefinition.build();

        assertNotNull(def.getCompletion());
    }

    @Test
    void build_hasSemanticData() {
        CaseDefinition def = StrategyEvaluationCaseDefinition.build();

        assertNotNull(def.getSemanticData());
        assertTrue(def.getSemanticData().containsKey("riskThresholds"));
    }
}
