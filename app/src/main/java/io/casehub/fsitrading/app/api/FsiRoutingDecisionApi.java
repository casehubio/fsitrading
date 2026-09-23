package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.model.ArenaRunEntity;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.QueryParam;

import java.util.List;

@McpDomain(value = "fsi/routing-decisions", basePath = "/api/fsi/routing-decisions")
@ApplicationScoped
public class FsiRoutingDecisionApi {

    @Inject
    EntityManager em;

    @PlatformQuery("List routing decisions")
    @RestPath("/")
    public List<ArenaRunEntity> listDecisions(@QueryParam("limit") Integer limit) {
        int maxResults = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        return em.createQuery(
                        "SELECT r FROM ArenaRunEntity r ORDER BY r.createdAt DESC",
                        ArenaRunEntity.class)
                .setMaxResults(maxResults)
                .getResultList();
    }

    @PlatformQuery("Get latest completed routing decision")
    @RestPath("/latest")
    public ArenaRunEntity latestDecision() {
        return em.createQuery(
                        "SELECT r FROM ArenaRunEntity r WHERE r.status = 'COMPLETED' ORDER BY r.createdAt DESC",
                        ArenaRunEntity.class)
                .setMaxResults(1)
                .getResultStream().findFirst().orElse(null);
    }
}
