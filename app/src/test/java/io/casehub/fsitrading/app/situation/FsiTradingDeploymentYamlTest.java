package io.casehub.fsitrading.app.situation;

import io.casehub.ops.deployment.DeploymentGoalLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FsiTradingDeploymentYamlTest {

    private final DeploymentGoalLoader loader = new DeploymentGoalLoader();

    @Test
    void deploymentYamlParsesIntoDeploymentGoals() {
        var goals = loader.load("casehub-deployment.yaml");

        assertThat(goals.agents()).hasSize(4);
        assertThat(goals.channels()).hasSize(3);
        assertThat(goals.caseTypes()).hasSize(2);
        assertThat(goals.trust()).hasSize(2);
        assertThat(goals.adaptations()).hasSize(3);
    }

    @Test
    void adaptationRulesHaveCorrectTriggers() {
        var goals = loader.load("casehub-deployment.yaml");

        assertThat(goals.adaptations().get(0).name())
                .isEqualTo("scale-risk-on-volatility");
        assertThat(goals.adaptations().get(0).trigger().situation())
                .isEqualTo("fsitrading.volatility-spike");

        assertThat(goals.adaptations().get(1).name())
                .isEqualTo("tighten-trust-on-anomaly");
        assertThat(goals.adaptations().get(1).trigger().situation())
                .isEqualTo("fsitrading.market-anomaly");

        assertThat(goals.adaptations().get(2).name())
                .isEqualTo("add-forensics-on-breach");
        assertThat(goals.adaptations().get(2).trigger().situation())
                .isEqualTo("fsitrading.active-breach");
    }

    @Test
    void agentTopologyMatchesSpec() {
        var goals = loader.load("casehub-deployment.yaml");

        var agentIds = goals.agents().stream()
                .map(e -> e.spec().agentId())
                .toList();
        assertThat(agentIds).containsExactly(
                "strategy-agent", "strategy-agent-2", "risk-agent", "audit-agent");
    }

    @Test
    void trustPoliciesMatch() {
        var goals = loader.load("casehub-deployment.yaml");

        assertThat(goals.trust().get(0).spec().capability())
                .isEqualTo("trade-execution");
        assertThat(goals.trust().get(1).spec().capability())
                .isEqualTo("risk-assessment");
    }
}
