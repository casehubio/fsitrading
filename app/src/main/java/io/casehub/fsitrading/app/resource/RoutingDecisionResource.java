package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.ArenaRunEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/routing/decisions")
@Produces(MediaType.APPLICATION_JSON)
public class RoutingDecisionResource {

    @Inject
    EntityManager em;

    @GET
    public List<ArenaRunEntity> listDecisions(@QueryParam("limit") Integer limit) {
        int maxResults = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        return em.createQuery(
                        "SELECT r FROM ArenaRunEntity r ORDER BY r.createdAt DESC",
                        ArenaRunEntity.class)
                .setMaxResults(maxResults)
                .getResultList();
    }

    @GET
    @Path("/latest")
    public ArenaRunEntity latestDecision() {
        return em.createQuery(
                        "SELECT r FROM ArenaRunEntity r WHERE r.status = 'COMPLETED' ORDER BY r.createdAt DESC",
                        ArenaRunEntity.class)
                .setMaxResults(1)
                .getResultStream().findFirst().orElse(null);
    }
}
