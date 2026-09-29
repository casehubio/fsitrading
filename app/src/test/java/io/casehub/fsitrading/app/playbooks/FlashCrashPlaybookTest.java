package io.casehub.fsitrading.app.playbooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.step.StepDefinitionFile;
import io.casehub.yaml.core.step.StepDefinitionParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlashCrashPlaybookTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Pattern STEP_REF = Pattern.compile("fsitrading\\.[a-z-]+");
    private static Set<String> catalogActions;
    private static Map<String, Object> playbook;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws IOException {
        try (var stream = FlashCrashPlaybookTest.class.getClassLoader()
                .getResourceAsStream("steps/trading-steps.yaml")) {
            Map<String, Object> yaml = YAML.readValue(stream, Map.class);
            StepDefinitionFile stepFile = StepDefinitionParser.parse(yaml);
            catalogActions = stepFile.actions().keySet().stream()
                    .map(a -> "fsitrading." + a)
                    .collect(Collectors.toSet());
        }
        try (var stream = FlashCrashPlaybookTest.class.getClassLoader()
                .getResourceAsStream("playbooks/flash-crash-response.yaml")) {
            assertThat(stream).as("playbook must exist on classpath").isNotNull();
            playbook = YAML.readValue(stream, Map.class);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasPlaybookMetadata() {
        Map<String, Object> meta = (Map<String, Object>) playbook.get("playbook");
        assertThat(meta).isNotNull();
        assertThat(meta.get("name")).isEqualTo("flash-crash-response");
        assertThat(meta).containsKey("trigger");
        assertThat(meta).containsKey("deadline");
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasAllStates() {
        Map<String, Object> states = (Map<String, Object>) playbook.get("states");
        assertThat(states).isNotNull();
        assertThat(states).containsKeys("DETECTED", "RESPONDING", "MONITORING", "ESCALATED");
    }

    @Test
    void detectedStateHaltsOrders() throws IOException {
        String content = new String(FlashCrashPlaybookTest.class.getClassLoader()
                                                                .getResourceAsStream("playbooks/flash-crash-response.yaml").readAllBytes());
        assertThat(content).contains("fsitrading.halt-new-orders");
    }

    @Test
    void allStepReferencesResolveAgainstCatalog() throws IOException {
        String content = new String(FlashCrashPlaybookTest.class.getClassLoader()
                                                                .getResourceAsStream("playbooks/flash-crash-response.yaml").readAllBytes());
        // Match step refs at line start (after whitespace and dash) — excludes ${config.fsitrading.*}
        Pattern lineStepRef = Pattern.compile("^\\s*-?\\s*(fsitrading\\.[a-z-]+):", Pattern.MULTILINE);
        Matcher matcher     = lineStepRef.matcher(content);
        while (matcher.find()) {
            String ref = matcher.group(1);
            assertThat(catalogActions)
                    .as("Step '%s' must resolve against catalog", ref)
                    .contains(ref);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void escalatedStateHasReconciliation() {
        String content;
        try {
            content = new String(FlashCrashPlaybookTest.class.getClassLoader()
                    .getResourceAsStream("playbooks/flash-crash-response.yaml").readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertThat(content).contains("fsitrading.reconcile-orders");
        assertThat(content).contains("fsitrading.release-orders");
    }

    @Test
    void hasModuleImports() throws IOException {
        String content = new String(FlashCrashPlaybookTest.class.getClassLoader()
                .getResourceAsStream("playbooks/flash-crash-response.yaml").readAllBytes());
        assertThat(content).contains("import: parallel-assessment");
        assertThat(content).contains("import: risk-gate");
    }
}
