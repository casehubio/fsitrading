package io.casehub.fsitrading.app.arena;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Map;
import java.util.Set;

@Alternative
@Priority(1)
@ApplicationScoped
public class FsiTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {

    private static final TrustRoutingPolicy ARENA_POLICY = new TrustRoutingPolicy(
            0.4,
            10,
            0.1,
            0.6,
            Map.of(
                    "return-magnitude", 0.3,
                    "hold-period-efficiency", 0.3,
                    "risk-adjusted-return", 0.3),
            false,
            null,
            Set.of(),
            0.3);

    @Override
    public String id() {
        return "fsi-arena";
    }

    @Override
    public TrustRoutingPolicy forCapability(String capabilityName) {
        if ("strategy-evaluation".equals(capabilityName)) {
            return ARENA_POLICY;
        }
        return TrustRoutingPolicy.DEFAULT;
    }
}
