package io.casehub.fsitrading.app.incident;

import io.casehub.api.engine.YamlCaseHub;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FsiOversightCaseHub extends YamlCaseHub {

    public FsiOversightCaseHub() {
        super("fsitrading/fsi-oversight.yaml");
    }
}
