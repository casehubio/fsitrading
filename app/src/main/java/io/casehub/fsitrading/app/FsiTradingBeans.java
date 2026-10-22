package io.casehub.fsitrading.app;

import io.casehub.api.spi.routing.RoutingSignalAssembler;
import io.casehub.api.spi.routing.RoutingSignalProvider;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.perception.narrative.DecisionNarrativePipeline;
import io.casehub.agency.perception.narrative.DecisionSignal;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class FsiTradingBeans {

    @Inject
    Instance<RoutingSignalProvider> signalProviders;

    @Inject
    Instance<AgentProvider> agentProviderInstance;

    @Produces
    @Singleton
    public RoutingSignalAssembler routingSignalAssembler() {
        var providers = StreamSupport.stream(signalProviders.spliterator(), false).toList();
        return new RoutingSignalAssembler(providers);
    }

    @Produces
    @ApplicationScoped
    public EventStreamBus<DecisionSignal> decisionSignalBus() {
        return new EventStreamBus<>();
    }

    @Produces
    @ApplicationScoped
    public DecisionNarrativePipeline decisionNarrativePipeline() {
        if (agentProviderInstance.isResolvable()) {
            return new DecisionNarrativePipeline(agentProviderInstance.get());
        }
        return new DecisionNarrativePipeline(null);
    }
}
