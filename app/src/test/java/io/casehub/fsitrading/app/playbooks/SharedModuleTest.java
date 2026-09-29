package io.casehub.fsitrading.app.playbooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.step.StepDefinitionFile;
import io.casehub.yaml.core.step.StepDefinitionParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SharedModuleTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Pattern STEP_REF = Pattern.compile("fsitrading\\.[a-z-]+");
    private static Set<String> catalogActions;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadCatalog() throws IOException {
        try (var stream = SharedModuleTest.class.getClassLoader()
                .getResourceAsStream("steps/trading-steps.yaml")) {
            Map<String, Object> yaml = YAML.readValue(stream, Map.class);
            StepDefinitionFile stepFile = StepDefinitionParser.parse(yaml);
            catalogActions = stepFile.actions().keySet().stream()
                    .map(a -> "fsitrading." + a)
                    .collect(Collectors.toSet());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"modules/risk-gate.yaml", "modules/parallel-assessment.yaml"})
    @SuppressWarnings("unchecked")
    void moduleYamlParses(String path) throws IOException {
        try (var stream = SharedModuleTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(stream).as("%s must exist on classpath", path).isNotNull();
            Map<String, Object> yaml = YAML.readValue(stream, Map.class);
            assertThat(yaml).containsKey("module");
            Map<String, Object> module = (Map<String, Object>) yaml.get("module");
            assertThat(module).containsKey("name");
            assertThat(module).containsKey("parameters");
            assertThat(yaml).containsKey("steps");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void riskGateModuleHasRequiredParameters() throws IOException {
        Map<String, Object> yaml = loadModule("modules/risk-gate.yaml");
        Map<String, Object> module = (Map<String, Object>) yaml.get("module");
        assertThat(module.get("name")).isEqualTo("risk-gate");
        Map<String, Object> params = (Map<String, Object>) module.get("parameters");
        assertThat(params).containsKeys("instrument", "proposed-action");
    }

    @Test
    @SuppressWarnings("unchecked")
    void parallelAssessmentModuleHasRequiredParameters() throws IOException {
        Map<String, Object> yaml = loadModule("modules/parallel-assessment.yaml");
        Map<String, Object> module = (Map<String, Object>) yaml.get("module");
        assertThat(module.get("name")).isEqualTo("parallel-assessment");
        Map<String, Object> params = (Map<String, Object>) module.get("parameters");
        assertThat(params).containsKeys("perspectives", "instrument");
    }

    @ParameterizedTest
    @ValueSource(strings = {"modules/risk-gate.yaml", "modules/parallel-assessment.yaml"})
    void allStepReferencesResolveAgainstCatalog(String path) throws IOException {
        String content = new String(SharedModuleTest.class.getClassLoader()
                .getResourceAsStream(path).readAllBytes());
        Matcher matcher = STEP_REF.matcher(content);
        while (matcher.find()) {
            String ref = matcher.group();
            assertThat(catalogActions)
                    .as("Step '%s' in %s must resolve against catalog", ref, path)
                    .contains(ref);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadModule(String path) throws IOException {
        try (var stream = SharedModuleTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            return YAML.readValue(stream, Map.class);
        }
    }
}
