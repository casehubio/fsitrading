package io.casehub.fsitrading.app.incident.store;

import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentTimelineRecord;
import io.casehub.fsitrading.spi.IncidentStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JpaIncidentStore implements IncidentStore {

    private final EntityManager em;

    public JpaIncidentStore(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(IncidentRecord record) {
        em.persist(IncidentEntity.from(record));
    }

    @Override
    public IncidentRecord findByCaseId(UUID caseId) {
        var entity = em.find(IncidentEntity.class, caseId);
        return entity != null ? entity.toRecord() : null;
    }

    @Override
    public List<IncidentRecord> findRecent(int limit) {
        return em.createQuery(
                        "SELECT e FROM IncidentEntity e ORDER BY e.createdAt DESC",
                        IncidentEntity.class)
                .setMaxResults(limit)
                .getResultStream()
                .map(IncidentEntity::toRecord)
                .toList();
    }

    @Override
    public List<IncidentRecord> findByStatus(String status) {
        return em.createQuery(
                        "SELECT e FROM IncidentEntity e WHERE e.status = :status ORDER BY e.createdAt DESC",
                        IncidentEntity.class)
                .setParameter("status", status)
                .getResultStream()
                .map(IncidentEntity::toRecord)
                .toList();
    }

    @Override
    public void updateStatus(UUID caseId, String status) {
        var entity = em.find(IncidentEntity.class, caseId);
        if (entity != null) {
            entity.setStatus(status);
        }
    }

    @Override
    public void addTimelineEntry(UUID caseId, IncidentTimelineRecord entry) {
        em.persist(IncidentTimelineEntity.from(caseId, entry));
    }

    @Override
    public List<IncidentTimelineRecord> getTimeline(UUID caseId) {
        return em.createQuery(
                        "SELECT e FROM IncidentTimelineEntity e WHERE e.caseId = :caseId ORDER BY e.occurredAt ASC",
                        IncidentTimelineEntity.class)
                .setParameter("caseId", caseId)
                .getResultStream()
                .map(IncidentTimelineEntity::toRecord)
                .toList();
    }
}
