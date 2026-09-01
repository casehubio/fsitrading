package io.casehub.fsitrading.app.cbr;

import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import io.casehub.neocortex.memory.cbr.WarpingConstraint;

public final class FsiCbrFeatureSchema {

    public static final String CASE_TYPE = PlanCbrCase.CBR_TYPE;

    public static final CbrFeatureSchema SCHEMA = CbrFeatureSchema.of(CASE_TYPE,
            FeatureField.categorical("event_type"),
            FeatureField.categorical("instrument_sector"),
            FeatureField.numeric("time_of_day", 0, 24,
                    new SimilaritySpec.GaussianDecay(2.0)),
            FeatureField.numeric("volatility_at_detection", 0, 100,
                    new SimilaritySpec.GaussianDecay(10.0)),
            FeatureField.numericList("volume_profile", 0, 1e9),
            FeatureField.timeSeries("price_action_pattern", "timestamp",
                    new SimilaritySpec.DtwSpec(new WarpingConstraint.SakoeChibaBand(5)),
                    FeatureField.numeric("timestamp", 0, Double.MAX_VALUE),
                    FeatureField.numeric("price", 0, Double.MAX_VALUE),
                    FeatureField.numeric("momentum", -1, 1)),
            FeatureField.discreteSequence("event_sequence",
                    FsiEventTypeSubstitutionCosts.build()));

    private FsiCbrFeatureSchema() {}
}
