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

class RiskEscalationPlaybookTest {

    private static final String PLAYBOOK_PATH = "playbooks/risk-escalation.yaml";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static Set<String> catalogActions;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws IOException {
        try (var stream = RiskEscalationPlaybookTest.class.getClassLoader()
                .getResourceAsStream("steps/trading-steps.yaml")) {
            Map<String, Object> yaml = YAML.readValue(stream, Map.class);
            StepDefinitionFile stepFile = StepDefinitionParser.parse(yaml);
            catalogActions = stepFile.actions().keySet().stream()
                    .map(a -> "fsitrading." + a).collect(Collectors.toSet());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasPlaybookMetadata() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(PLAYBOOK_PATH)) {
            Map<String, Object> playbook = YAML.readValue(stream, Map.class);
            Map<String, Object> meta = (Map<String, Object>) playbook.get("playbook");
            assertThat(meta).isNotNull();
            assertThat(meta.get("name")).isEqualTo("risk-escalation");
        }
    }

    @Test
    void allStepReferencesResolve() throws IOException {
        String content = new String(getClass().getClassLoader()
                .getResourceAsStream(PLAYBOOK_PATH).readAllBytes());
        Pattern lineStepRef = Pattern.compile("^\\s*-?\\s*(fsitrading\\.[a-z-]+):", Pattern.MULTILINE);
        Matcher matcher = lineStepRef.matcher(content);
        while (matcher.find()) {
            assertThat(catalogActions).as("Step '%s'", matcher.group(1)).contains(matcher.group(1));
        }
    }

    @Test
    void hasSemaphoreLifecycle() throws IOException {
        String content = new String(getClass().getClassLoader()
                .getResourceAsStream(PLAYBOOK_PATH).readAllBytes());
        assertThat(content).contains("fsitrading.halt-new-orders");
        assertThat(content).contains("fsitrading.release-orders");
    }

    @Test
    void hasWaitState() throws IOException {
        String content = new String(getClass().getClassLoader()
                .getResourceAsStream(PLAYBOOK_PATH).readAllBytes());
        assertThat(content).contains("wait-state");
        assertThat(content).contains("risk-committee-approval");
    }
}
