package io.casehub.fsitrading.app.incident;

import io.casehub.platform.api.path.Path;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiSlaBreachPolicyTest {

    private final FsiSlaBreachPolicy policy = new FsiSlaBreachPolicy();

    @Test
    void id_returnsPolicyName() {
        assertEquals("fsi-overnight-sla", policy.id());
    }

    @Test
    void claimExpired_firstTier_escalates() {
        var ctx = breach(BreachType.CLAIM_EXPIRED, Set.of("fsi-oncall"));
        var decision = policy.onBreach(ctx);
        assertInstanceOf(BreachDecision.EscalateTo.class, decision);
        var escalate = (BreachDecision.EscalateTo) decision;
        assertTrue(escalate.groups().contains("oncall-escalation"));
    }

    @Test
    void claimExpired_alreadyEscalated_exhausted() {
        var ctx = breach(BreachType.CLAIM_EXPIRED, Set.of("fsi-oncall", "oncall-escalation"));
        var decision = policy.onBreach(ctx);
        assertInstanceOf(BreachDecision.Exhausted.class, decision);
    }

    @Test
    void completionExpired_exhausted() {
        var ctx = breach(BreachType.COMPLETION_EXPIRED, Set.of("fsi-oncall"));
        var decision = policy.onBreach(ctx);
        assertInstanceOf(BreachDecision.Exhausted.class, decision);
    }

    @Test
    void completionExpired_alreadyEscalated_exhausted() {
        var ctx = breach(BreachType.COMPLETION_EXPIRED, Set.of("fsi-oncall", "oncall-escalation"));
        var decision = policy.onBreach(ctx);
        assertInstanceOf(BreachDecision.Exhausted.class, decision);
    }

    @Test
    void escalation_doesNotIncludeOriginalGroup() {
        var ctx = breach(BreachType.CLAIM_EXPIRED, Set.of("fsi-oncall"));
        var escalate = (BreachDecision.EscalateTo) policy.onBreach(ctx);
        assertTrue(escalate.groups().contains("oncall-escalation"));
    }

    private SlaBreachContext breach(BreachType type, Set<String> groups) {
        return new SlaBreachContext(type,
                new BreachedTask(UUID.randomUUID(), "ref-1", "Risk gate", groups),
                Path.of("fsitrading"), null);
    }
}
