package io.casehub.fsitrading.app.cbr;

import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.FeatureField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FsiCbrFeatureSchemaTest {

    @Test
    void schemaHasSevenFields() {
        CbrFeatureSchema schema = FsiCbrFeatureSchema.SCHEMA;
        assertThat(schema.fields()).hasSize(7);
        assertThat(schema.caseType()).isEqualTo("plan");
    }

    @Test
    void fieldNamesMatchSpec() {
        var names = FsiCbrFeatureSchema.SCHEMA.fields().stream()
                .map(FeatureField::name).toList();
        assertThat(names).containsExactly(
                "event_type", "instrument_sector", "time_of_day",
                "volatility_at_detection", "volume_profile",
                "price_action_pattern", "event_sequence");
    }

    @Test
    void timeSeriesFieldHasDtwSpec() {
        var ts = FsiCbrFeatureSchema.SCHEMA.fields().stream()
                .filter(f -> f.name().equals("price_action_pattern"))
                .findFirst().orElseThrow();
        assertThat(ts).isInstanceOf(FeatureField.TimeSeries.class);
        var timeSeries = (FeatureField.TimeSeries) ts;
        assertThat(timeSeries.similaritySpec())
                .isInstanceOf(io.casehub.neocortex.memory.cbr.SimilaritySpec.DtwSpec.class);
    }

    @Test
    void discreteSequenceFieldHasEditDistanceSpec() {
        var ds = FsiCbrFeatureSchema.SCHEMA.fields().stream()
                .filter(f -> f.name().equals("event_sequence"))
                .findFirst().orElseThrow();
        assertThat(ds).isInstanceOf(FeatureField.DiscreteSequence.class);
        var seq = (FeatureField.DiscreteSequence) ds;
        assertThat(seq.similaritySpec())
                .isInstanceOf(io.casehub.neocortex.memory.cbr.SimilaritySpec.EditDistanceSpec.class);
    }

    @Test
    void substitutionCostsIncludeExplicitPairs() {
        var costs = FsiEventTypeSubstitutionCosts.build();
        assertThat(costs.substitutionSimilarities())
                .containsKey("FLASH_CRASH");
        assertThat(costs.substitutionSimilarities()
                .getOrDefault("FLASH_CRASH", java.util.Map.of())
                .getOrDefault("LIQUIDITY_DROP", 0.0))
                .isEqualTo(0.7);
    }
}
