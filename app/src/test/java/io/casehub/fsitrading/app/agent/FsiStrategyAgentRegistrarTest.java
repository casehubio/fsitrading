package io.casehub.fsitrading.app.agent;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.model.StrategyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FsiStrategyAgentRegistrarTest {

    private final FsiStrategyAgentRegistrar registrar = new FsiStrategyAgentRegistrar();

    @Test
    void registersAllSevenStrategies() {
        List<AgentDescriptor> descriptors = registrar.descriptors();
        assertEquals(7, descriptors.size());
    }

    @Test
    void momentumDescriptorHasCorrectFields() {
        var descriptors = registrar.descriptors();
        var momentum = descriptors.stream()
                .filter(d -> d.agentId().equals(FsiActorIdentity.forStrategy(StrategyType.MOMENTUM)))
                .findFirst().orElseThrow();

        assertEquals("momentum", momentum.name());
        assertEquals("v1", momentum.version());
        assertEquals("casehub-fsitrading", momentum.provider());
        assertEquals("rule", momentum.modelFamily());
        assertEquals("executor", momentum.slot());
        assertEquals(2, momentum.capabilities().size());
        assertEquals("momentum", momentum.capabilities().get(0).name());
        assertEquals("trend-analysis", momentum.capabilities().get(1).name());
        assertNotNull(momentum.disposition());
    }

    @Test
    void allDescriptorsHaveDisposition() {
        for (var descriptor : registrar.descriptors()) {
            assertNotNull(descriptor.disposition(), descriptor.name() + " missing disposition");
        }
    }

    @Test
    void allDescriptorsHaveUniqueAgentIds() {
        var descriptors = registrar.descriptors();
        var ids = descriptors.stream().map(AgentDescriptor::agentId).distinct().count();
        assertEquals(7, ids);
    }

    @Test
    void overnightRiskDescriptorHasDefensiveCapability() {
        var descriptors = registrar.descriptors();
        var overnight = descriptors.stream()
                .filter(d -> d.agentId().equals(FsiActorIdentity.forStrategy(StrategyType.OVERNIGHT_RISK_MANAGEMENT)))
                .findFirst().orElseThrow();

        assertTrue(overnight.capabilities().stream().anyMatch(c -> c.name().equals("defensive")));
    }
}
