package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.app.model.ArenaRunEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class ArenaRunRepository {

    @Inject
    EntityManager em;

    public ArenaRunEntity findByIdempotencyKey(UUID key) {
        return em.createQuery(
                        "SELECT r FROM ArenaRunEntity r WHERE r.idempotencyKey = :key",
                        ArenaRunEntity.class)
                .setParameter("key", key)
                .getResultStream().findFirst().orElse(null);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void persist(ArenaRunEntity run) {
        em.persist(run);
        em.flush();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void complete(ArenaRunEntity run, String resultJson) {
        var managed = em.merge(run);
        managed.complete(resultJson);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void fail(ArenaRunEntity run, String reason) {
        var managed = em.merge(run);
        managed.fail(reason);
    }
}
