package io.casehub.fsitrading.app.incident;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OvernightIncidentCaseHub extends YamlCaseHub {

    private final OvernightIncidentCaseDescriptor descriptor = new OvernightIncidentCaseDescriptor();

    public OvernightIncidentCaseHub() {
        super("fsitrading/overnight-incident.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        // Worker functions will be wired by Task 4 (response agents).
        // The YAML-loaded workers with WorkerFunction.NONE are sufficient
        // for the case structure — agents replace them at runtime.
    }

    public OvernightIncidentCaseDescriptor getDescriptor() {
        return descriptor;
    }
}
