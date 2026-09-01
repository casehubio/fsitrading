package io.casehub.fsitrading.app.incident;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.fsitrading.app.cbr.FsiCbrFeatureSchema;
import io.casehub.fsitrading.app.cbr.FsiFeatureExtractor;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OvernightIncidentCaseHub extends YamlCaseHub {

    private final OvernightIncidentCaseDescriptor descriptor = new OvernightIncidentCaseDescriptor();

    @Inject
    CbrCaseMemoryStore cbrStore;

    @Inject
    FsiFeatureExtractor fsiFeatureExtractor;

    public OvernightIncidentCaseHub() {
        super("fsitrading/overnight-incident.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        descriptor.augmentWorkers(definition);

        cbrStore.registerSchema(FsiCbrFeatureSchema.SCHEMA);

        CbrConfig yamlConfig = definition.getCbrConfig();
        if (yamlConfig != null) {
            definition.setCbrConfig(CbrConfig.builder()
                    .featureExtractor(fsiFeatureExtractor::extract)
                    .topK(yamlConfig.topK())
                    .minSimilarity(yamlConfig.minSimilarity())
                    .temporalDecayHalfLifeDays(yamlConfig.temporalDecayHalfLifeDays())
                    .domain("fsitrading")
                    .caseType(PlanCbrCase.CBR_TYPE)
                    .cbrType(PlanCbrCase.CBR_TYPE)
                    .timing(CbrConfig.CbrRetrievalTiming.CASE_LIFETIME)
                    .weight("event_type", 0.15)
                    .weight("instrument_sector", 0.10)
                    .weight("time_of_day", 0.10)
                    .weight("volatility_at_detection", 0.10)
                    .weight("volume_profile", 0.10)
                    .weight("price_action_pattern", 0.25)
                    .weight("event_sequence", 0.20)
                    .build());
        }
    }

    public OvernightIncidentCaseDescriptor getDescriptor() {
        return descriptor;
    }
}
