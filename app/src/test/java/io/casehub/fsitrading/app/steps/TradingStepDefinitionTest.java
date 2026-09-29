package io.casehub.fsitrading.app.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinitionFile;
import io.casehub.yaml.core.step.StepDefinitionParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TradingStepDefinitionTest {

    private static StepDefinitionFile stepFile;

    @BeforeAll
    static void loadStepDefinitions() throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());
        try (var stream = TradingStepDefinitionTest.class.getClassLoader()
                .getResourceAsStream("steps/trading-steps.yaml")) {
            assertThat(stream).as("steps/trading-steps.yaml must exist on classpath").isNotNull();
            Map<String, Object> yaml = mapper.readValue(stream, Map.class);
            stepFile = StepDefinitionParser.parse(yaml);
        }
    }

    @Test
    void namespaceIsFsitrading() {
        assertThat(stepFile.namespace()).isEqualTo("fsitrading");
    }

    @Test
    void allSixteenActionsPresent() {
        List<String> expected = List.of(
                "assess-risk", "fetch-position", "submit-order", "evaluate-strategy",
                "check-market-data", "trigger-risk-gate", "halt-new-orders", "release-orders",
                "close-position", "adjust-risk-thresholds", "reconcile-orders", "notify-risk-desk",
                "analyse-sentiment", "explain-decision", "calculate-var", "check-fix-session");

        assertThat(stepFile.actions()).containsKeys(expected.toArray(String[]::new));
        assertThat(stepFile.actions()).hasSize(16);
    }

    @Test
    void mcpBindingsResolveCorrectTools() {
        Map<String, String> expectedMcpTools = Map.ofEntries(
                Map.entry("assess-risk", "fsi/risk/assess"),
                Map.entry("fetch-position", "fsi/positions/getByInstrument"),
                Map.entry("submit-order", "fsi/orders/submit"),
                Map.entry("evaluate-strategy", "fsi/strategies/evaluate"),
                Map.entry("check-market-data", "fsi/market-data/snapshot"),
                Map.entry("trigger-risk-gate", "fsi/risk/gate"),
                Map.entry("halt-new-orders", "fsi/orders/halt"),
                Map.entry("release-orders", "fsi/orders/release"),
                Map.entry("close-position", "fsi/positions/close"),
                Map.entry("adjust-risk-thresholds", "fsi/risk/adjustThresholds"),
                Map.entry("reconcile-orders", "fsi/orders/reconcile"));

        for (var entry : expectedMcpTools.entrySet()) {
            var action = stepFile.actions().get(entry.getKey());
            assertThat(action).as("Action '%s' should exist", entry.getKey()).isNotNull();
            assertThat(action.invoke()).isInstanceOf(InvokeBinding.Mcp.class);
            assertThat(((InvokeBinding.Mcp) action.invoke()).tool()).isEqualTo(entry.getValue());
        }
    }

    @Test
    void restBindingForNotifyRiskDesk() {
        var action = stepFile.actions().get("notify-risk-desk");
        assertThat(action.invoke()).isInstanceOf(InvokeBinding.Rest.class);
        var rest = (InvokeBinding.Rest) action.invoke();
        assertThat(rest.method()).isEqualTo("POST");
    }

    @Test
    void agentBindingsExist() {
        var sentiment = stepFile.actions().get("analyse-sentiment");
        assertThat(sentiment.invoke()).isInstanceOf(InvokeBinding.Agent.class);
        assertThat(((InvokeBinding.Agent) sentiment.invoke()).descriptor()).isEqualTo("sentiment-analyser");

        var explainer = stepFile.actions().get("explain-decision");
        assertThat(explainer.invoke()).isInstanceOf(InvokeBinding.Agent.class);
        assertThat(((InvokeBinding.Agent) explainer.invoke()).descriptor()).isEqualTo("decision-explainer");
    }

    @Test
    void processBindingsExist() {
        var var_ = stepFile.actions().get("calculate-var");
        assertThat(var_.invoke()).isInstanceOf(InvokeBinding.Script.class);

        var fix = stepFile.actions().get("check-fix-session");
        assertThat(fix.invoke()).isInstanceOf(InvokeBinding.Process.class);
    }

    @Test
    void allActionsHaveDescriptions() {
        for (var entry : stepFile.actions().entrySet()) {
            assertThat(entry.getValue().description())
                    .as("Action '%s' should have a description", entry.getKey())
                    .isNotBlank();
        }
    }

    @Test
    void assessRiskHasTypedInputs() {
        var action = stepFile.actions().get("assess-risk");
        assertThat(action.inputs()).containsKey("instrument");
        assertThat(action.inputs().get("instrument").required()).isTrue();
    }
}
