package io.casehub.fsitrading.app.arena;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingPromptSection;
import io.casehub.fsitrading.app.model.ArenaRunEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ApplicationScoped
public class FsiArenaRoutingPromptSection implements RoutingPromptSection {

    @Inject
    EntityManager em;

    @Override
    public @Nullable String render(AgentRoutingContext context, List<AgentCandidate> eligible) {
        String instrument = extractInstrument(context);
        if (instrument == null) {
            return null;
        }

        List<ArenaRunEntity> recentRuns = em.createQuery(
                        "SELECT r FROM ArenaRunEntity r WHERE r.instrument = :instrument AND r.status != 'IN_FLIGHT' ORDER BY r.createdAt DESC",
                        ArenaRunEntity.class)
                .setParameter("instrument", instrument)
                .setMaxResults(5)
                .getResultList();

        if (recentRuns.isEmpty()) {
            return null;
        }

        var sb = new StringBuilder();
        sb.append("Recent arena outcomes for %s (%d runs):\n".formatted(instrument, recentRuns.size()));
        for (var run : recentRuns) {
            sb.append("  - %s: %s".formatted(run.getStatus(), run.getCreatedAt()));
            if (run.getReason() != null) {
                sb.append(" (%s)".formatted(run.getReason()));
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private @Nullable String extractInstrument(AgentRoutingContext context) {
        if (context.caseContext() != null && context.caseContext().has("instrument")) {
            return context.caseContext().get("instrument").asText();
        }
        return null;
    }
}
