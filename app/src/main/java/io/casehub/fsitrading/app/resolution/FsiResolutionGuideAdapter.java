package io.casehub.fsitrading.app.resolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.api.spi.CorpusSourceAdapter;
import io.casehub.api.spi.GuidanceStepInput;
import io.casehub.api.spi.ResolutionGuideInput;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiResolutionGuideAdapter implements CorpusSourceAdapter {

    private static final String[] RESOLUTION_FILES = {
            "resolution/counterparty-default.yaml",
            "resolution/regulatory-inquiry.yaml",
            "resolution/unprecedented-event.yaml",
            "resolution/system-failure.yaml",
            "resolution/margin-call.yaml"
    };

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Override
    public String id() {
        return "fsi-trading-resolutions";
    }

    @Override
    public List<ResolutionGuideInput> discover(String tenancyId) {
        return java.util.Arrays.stream(RESOLUTION_FILES)
                .map(this::loadGuide)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private ResolutionGuideInput loadGuide(String resourcePath) {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Resolution file not found: " + resourcePath);
            }
            Map<String, Object> yaml = YAML.readValue(stream, Map.class);

            String documentId = (String) yaml.get("document-id");
            String problem = (String) yaml.get("problem");
            String solution = (String) yaml.get("solution");
            String domain = (String) yaml.get("domain");
            Map<String, Object> features = (Map<String, Object>) yaml.get("features");

            List<Map<String, Object>> rawSteps = (List<Map<String, Object>>) yaml.get("steps");
            List<GuidanceStepInput> steps = rawSteps.stream()
                    .map(s -> new GuidanceStepInput(
                            (String) s.get("description"),
                            (String) s.get("preconditions"),
                            (String) s.get("expected-outcome"),
                            (String) s.get("automation-hint")))
                    .toList();

            return new ResolutionGuideInput(documentId, problem, solution,
                    steps, features, domain, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load resolution guide: " + resourcePath, e);
        }
    }
}
