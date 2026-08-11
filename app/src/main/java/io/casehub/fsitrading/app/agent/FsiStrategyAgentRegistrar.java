package io.casehub.fsitrading.app.agent;

import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.spi.AgentDescriptorRegistrar;
import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiStrategyAgentRegistrar implements AgentDescriptorRegistrar {

    static final String PROVIDER = "casehub-fsitrading";
    static final String MODEL_FAMILY = "rule";
    static final String SLOT = "executor";
    static final String VERSION = "v1";

    private static final Map<StrategyType, List<String>> CAPABILITIES = Map.of(
            StrategyType.MOMENTUM, List.of("momentum", "trend-analysis"),
            StrategyType.MEAN_REVERSION, List.of("mean-reversion", "statistical"),
            StrategyType.STATISTICAL_ARBITRAGE, List.of("statistical-arbitrage", "pairs"),
            StrategyType.MARKET_MAKING, List.of("market-making", "liquidity"),
            StrategyType.EVENT_DRIVEN, List.of("event-driven", "news"),
            StrategyType.PORTFOLIO_REBALANCE, List.of("portfolio-rebalance", "allocation"),
            StrategyType.OVERNIGHT_RISK_MANAGEMENT, List.of("overnight-risk", "defensive"));

    @Override
    public List<AgentDescriptor> descriptors() {
        return Arrays.stream(StrategyType.values())
                .map(this::buildDescriptor)
                .toList();
    }

    private AgentDescriptor buildDescriptor(StrategyType type) {
        var caps = CAPABILITIES.get(type).stream()
                .map(name -> new AgentCapability(name, null, null, null, null, null,
                        null, null, null, null, null))
                .toList();

        return AgentDescriptor.builder()
                .agentId(FsiActorIdentity.forStrategy(type))
                .name(FsiActorIdentity.capabilityTag(type))
                .version(VERSION)
                .provider(PROVIDER)
                .modelFamily(MODEL_FAMILY)
                .slot(SLOT)
                .capabilities(caps)
                .disposition(FsiDispositionProfiles.forType(type))
                .tenancyId(TenancyConstants.DEFAULT_TENANT_ID)
                .build();
    }
}
