package io.casehub.fsitrading.app.situation;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FsiTradingMarketMonitoringYamlTest {

    @SuppressWarnings("unchecked")
    @Test
    void pipelineYamlParsesCorrectly() {
        InputStream is = getClass().getClassLoader().getResourceAsStream(
                "META-INF/summarisation/fsitrading-market-monitoring.yaml");
        assertThat(is).isNotNull();

        Map<String, Object> root = new Yaml().load(is);
        Map<String, Object> pipeline = (Map<String, Object>) root.get("pipeline");
        assertThat(pipeline).isNotNull();
        assertThat(pipeline.get("name")).isEqualTo("fsitrading-market-monitoring");

        Map<String, Object> source = (Map<String, Object>) pipeline.get("source");
        assertThat(source.get("type-prefix")).isEqualTo("io.casehub.fsitrading.market.");

        List<Map<String, Object>> levels = (List<Map<String, Object>>) pipeline.get("levels");
        assertThat(levels).hasSize(2);
        assertThat(levels.get(0).get("name")).isEqualTo("signals");
        assertThat(levels.get(1).get("name")).isEqualTo("conditions");
    }

    @SuppressWarnings("unchecked")
    @Test
    void signalsLevelHasFiveClassificationRules() {
        InputStream is = getClass().getClassLoader().getResourceAsStream(
                "META-INF/summarisation/fsitrading-market-monitoring.yaml");
        Map<String, Object> root = new Yaml().load(is);
        Map<String, Object> pipeline = (Map<String, Object>) root.get("pipeline");
        List<Map<String, Object>> levels = (List<Map<String, Object>>) pipeline.get("levels");
        Map<String, Object> signals = levels.get(0);
        Map<String, Object> summariser = (Map<String, Object>) signals.get("summariser");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) summariser.get("rules");
        assertThat(rules).hasSize(5);
        assertThat(rules.get(0).get("name")).isEqualTo("price-spike");
        assertThat(rules.get(4).get("name")).isEqualTo("normal-tick");
    }

    @SuppressWarnings("unchecked")
    @Test
    void conditionsLevelHasThreeStatesAndFiveTransitions() {
        InputStream is = getClass().getClassLoader().getResourceAsStream(
                "META-INF/summarisation/fsitrading-market-monitoring.yaml");
        Map<String, Object> root = new Yaml().load(is);
        Map<String, Object> pipeline = (Map<String, Object>) root.get("pipeline");
        List<Map<String, Object>> levels = (List<Map<String, Object>>) pipeline.get("levels");
        Map<String, Object> conditions = levels.get(1);
        Map<String, Object> summariser = (Map<String, Object>) conditions.get("summariser");
        List<String> states = (List<String>) summariser.get("states");
        assertThat(states).containsExactly("STABLE", "VOLATILE", "ANOMALOUS");
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) summariser.get("transitions");
        assertThat(transitions).hasSize(5);
    }

    @SuppressWarnings("unchecked")
    @Test
    void emitTypesMatchEventTypeConstants() {
        InputStream is = getClass().getClassLoader().getResourceAsStream(
                "META-INF/summarisation/fsitrading-market-monitoring.yaml");
        Map<String, Object> root = new Yaml().load(is);
        Map<String, Object> pipeline = (Map<String, Object>) root.get("pipeline");
        List<Map<String, Object>> levels = (List<Map<String, Object>>) pipeline.get("levels");

        Map<String, Object> signalEmit = (Map<String, Object>) levels.get(0).get("emit");
        assertThat(signalEmit.get("cloud-event-type")).isEqualTo(FsiTradingEventTypes.SIGNAL);

        Map<String, Object> conditionEmit = (Map<String, Object>) levels.get(1).get("emit");
        assertThat(conditionEmit.get("cloud-event-type")).isEqualTo(FsiTradingEventTypes.CONDITION);
    }
}
