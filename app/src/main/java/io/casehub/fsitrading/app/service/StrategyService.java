package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.StrategyEntity;
import io.casehub.fsitrading.model.StrategyType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class StrategyService {

    @Inject
    EntityManager em;

    @Transactional
    public StrategyEntity create(String name, StrategyType type) {
        var entity = new StrategyEntity(UUID.randomUUID(), name, type);
        em.persist(entity);
        return entity;
    }

    public StrategyEntity findById(UUID id) {
        return em.find(StrategyEntity.class, id);
    }

    public List<StrategyEntity> findAll() {
        return em.createQuery("SELECT s FROM StrategyEntity s ORDER BY s.name",
                        StrategyEntity.class)
                .getResultList();
    }

    public List<StrategyEntity> findActive() {
        return em.createQuery(
                        "SELECT s FROM StrategyEntity s WHERE s.active = true ORDER BY s.name",
                        StrategyEntity.class)
                .getResultList();
    }
}
