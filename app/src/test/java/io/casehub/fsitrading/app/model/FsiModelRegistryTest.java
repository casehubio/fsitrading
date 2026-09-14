package io.casehub.fsitrading.app.model;

import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelTier;
import io.casehub.platform.model.InMemoryModelRegistry;
import io.casehub.platform.model.SeedCatalogModelSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FsiModelRegistryTest {

    private InMemoryModelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryModelRegistry();
        var                   source = new SeedCatalogModelSource();
        List<ModelDescriptor> models = source.refresh();
        registry.replaceSource("seed-catalog", 0, models);
    }

    @Test
    void seedCatalogLoadsThreeModels() {
        assertThat(registry.all()).hasSize(3);
    }

    @Test
    void flagshipResolvesToOpus() {
        var results = registry.query(ModelQuery.builder().tier(ModelTier.FLAGSHIP).build());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo("claude-opus-4");
    }

    @Test
    void standardResolvesToSonnet() {
        var results = registry.query(ModelQuery.builder().tier(ModelTier.STANDARD).build());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo("claude-sonnet-4");
    }

    @Test
    void fastResolvesToHaiku() {
        var results = registry.query(ModelQuery.builder().tier(ModelTier.FAST).build());
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo("claude-haiku-4");
    }

    @Test
    void allModelsHaveToolUseCapability() {
        assertThat(registry.all())
            .allMatch(m -> m.capabilities().contains("tool-use"));
    }

    @Test
    void flagshipHasReasoningCapability() {
        var flagship = registry.resolveById("claude-opus-4").orElseThrow();
        assertThat(flagship.capabilities()).contains("reasoning");
    }
}
