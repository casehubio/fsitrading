package io.casehub.fsitrading.app.incident;

import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.spi.SlaBreachPolicy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FsiSlaBreachPolicy implements SlaBreachPolicy {

    private static final String ESCALATION_GROUP = "oncall-escalation";

    @Override
    public String id() {
        return "fsi-overnight-sla";
    }

    @Override
    public BreachDecision onBreach(SlaBreachContext context) {
        boolean alreadyEscalated = context.task().candidateGroups()
                .contains(ESCALATION_GROUP);

        if (context.breachType() == BreachType.COMPLETION_EXPIRED || alreadyEscalated) {
            return new BreachDecision.Exhausted("SLA exhausted — auto-executing");
        }

        return BreachDecision.EscalateTo.to(ESCALATION_GROUP);
    }
}
