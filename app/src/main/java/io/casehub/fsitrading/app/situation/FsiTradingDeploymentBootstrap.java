package io.casehub.fsitrading.app.situation;

import io.casehub.desiredstate.api.CompilationResult;
import io.casehub.desiredstate.api.DesiredStateGraphFactory;
import io.casehub.ops.deployment.DeploymentGoalCompiler;
import io.casehub.ops.deployment.DeploymentGoalLoader;
import io.casehub.ops.deployment.adaptation.DeploymentAdaptiveSituationRecompiler;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.stream.Collectors;

@ApplicationScoped
public class FsiTradingDeploymentBootstrap {

    private static final Logger log = Logger.getLogger(FsiTradingDeploymentBootstrap.class);

    @Inject
    DeploymentGoalLoader goalLoader;

    @Inject
    DeploymentAdaptiveSituationRecompiler recompiler;

    @Inject
    DeploymentGoalCompiler compiler;

    @Inject
    DesiredStateGraphFactory graphFactory;

    @Inject
    FsiTradingSituationDefinitionProvider situationProvider;

    void onStart(@Observes StartupEvent ev) {
        var goals = goalLoader.load("casehub-deployment.yaml");

        var clearanceWindows = situationProvider.registrations().stream()
                .collect(Collectors.toMap(
                        r -> r.definition().situationId(),
                        r -> r.definition().correlationWindow() != null
                                ? r.definition().correlationWindow()
                                : Duration.ofMinutes(5)));

        String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        recompiler.register(tenancyId, goals, clearanceWindows, graphFactory);

        var result = compiler.compile(goals, graphFactory);
        if (result instanceof CompilationResult.SingleGraph single) {
            log.infof("fsitrading deployment registered: tenancy=%s, nodes=%d, adaptations=%d",
                    tenancyId, single.graph().nodes().size(), goals.adaptations().size());
        }
    }
}
