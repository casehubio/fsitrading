package io.casehub.fsitrading;

import io.casehub.fsitrading.model.StrategyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class FsiActorIdentityTest {

    @ParameterizedTest
    @EnumSource(StrategyType.class)
    void everyStrategyTypeProducesValidActorId(StrategyType type) {
        var actorId = FsiActorIdentity.forStrategy(type);
        assertNotNull(actorId);
        assertTrue(actorId.matches("[\\w-]+:[\\w-]+@[\\w.]+"),
                "actorId must match ActorTypeResolver AGENT regex: " + actorId);
        assertTrue(actorId.startsWith("rule:"), "must start with rule: — got: " + actorId);
        assertTrue(actorId.endsWith("@v1"), "must end with @v1 — got: " + actorId);
    }

    @Test
    void momentumActorId() {
        assertEquals("rule:momentum@v1", FsiActorIdentity.forStrategy(StrategyType.MOMENTUM));
    }

    @Test
    void meanReversionActorId() {
        assertEquals("rule:mean-reversion@v1", FsiActorIdentity.forStrategy(StrategyType.MEAN_REVERSION));
    }

    @Test
    void statisticalArbitrageActorId() {
        assertEquals("rule:statistical-arbitrage@v1",
                FsiActorIdentity.forStrategy(StrategyType.STATISTICAL_ARBITRAGE));
    }

    @Test
    void overnightRiskManagementActorId() {
        assertEquals("rule:overnight-risk-management@v1",
                FsiActorIdentity.forStrategy(StrategyType.OVERNIGHT_RISK_MANAGEMENT));
    }

    @ParameterizedTest
    @EnumSource(StrategyType.class)
    void actorRoleIsDerived(StrategyType type) {
        var role = FsiActorIdentity.actorRole(type);
        assertNotNull(role);
        assertTrue(role.endsWith("-strategy"), "role must end with -strategy: " + role);
    }

    @Test
    void momentumActorRole() {
        assertEquals("momentum-strategy", FsiActorIdentity.actorRole(StrategyType.MOMENTUM));
    }

    @ParameterizedTest
    @EnumSource(StrategyType.class)
    void capabilityTagMatchesFsiCapabilities(StrategyType type) {
        var tag = FsiActorIdentity.capabilityTag(type);
        assertNotNull(tag);
        assertFalse(tag.isBlank());
    }

    @Test
    void momentumCapabilityTag() {
        assertEquals(FsiCapabilities.MOMENTUM, FsiActorIdentity.capabilityTag(StrategyType.MOMENTUM));
    }

    @Test
    void meanReversionCapabilityTag() {
        assertEquals(FsiCapabilities.MEAN_REVERSION,
                FsiActorIdentity.capabilityTag(StrategyType.MEAN_REVERSION));
    }

    @Test
    void nullStrategyTypeThrows() {
        assertThrows(NullPointerException.class, () -> FsiActorIdentity.forStrategy(null));
        assertThrows(NullPointerException.class, () -> FsiActorIdentity.actorRole(null));
        assertThrows(NullPointerException.class, () -> FsiActorIdentity.capabilityTag(null));
    }
}
