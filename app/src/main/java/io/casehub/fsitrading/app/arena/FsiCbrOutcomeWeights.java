package io.casehub.fsitrading.app.arena;

import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.blocks.routing.agent.CbrOutcomeWeights;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Map;

@Alternative
@Priority(1)
@ApplicationScoped
public class FsiCbrOutcomeWeights implements CbrOutcomeWeights {

    private static final Map<RoutingOutcome, Double> WEIGHTS = Map.of(
            RoutingOutcome.SUCCESS, 1.0,
            RoutingOutcome.FAILURE, 0.0,
            RoutingOutcome.GATE_REJECTED, 0.1,
            RoutingOutcome.GATE_EXPIRED, 0.3,
            RoutingOutcome.DECLINED, 0.0,
            RoutingOutcome.CANCELLED, 0.2);

    @Override
    public Map<RoutingOutcome, Double> weights() {
        return WEIGHTS;
    }
}
