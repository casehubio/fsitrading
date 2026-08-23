package io.casehub.fsitrading.app.incident.store;

import io.casehub.fsitrading.model.IncidentRecord;
import io.casehub.fsitrading.model.IncidentSeverity;
import io.casehub.fsitrading.model.IncidentSummary;
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

    @Override
    public IncidentSummary getSummary() {
        @SuppressWarnings("unchecked")
        List<Object[]> severityRows = em.createQuery(
                                                "SELECT e.severity, COUNT(e) FROM IncidentEntity e GROUP BY e.severity")
                                        .getResultList();

        var bySeverity = new java.util.ArrayList<IncidentSummary.SeverityCount>();
        for (IncidentSeverity sev : IncidentSeverity.values()) {
            long count = severityRows.stream()
                                     .filter(r -> r[0] == sev)
                                     .map(r -> (Long) r[1])
                                     .findFirst().orElse(0L);
            bySeverity.add(new IncidentSummary.SeverityCount(sev.name(), count));
        }

        long totalActive = em.createQuery(
                                     "SELECT COUNT(e) FROM IncidentEntity e WHERE e.status <> 'CLOSED'", Long.class)
                             .getSingleResult();

        boolean anyBreached = em.createQuery(
                                        "SELECT COUNT(e) FROM IncidentEntity e WHERE e.status <> 'CLOSED' AND e.completionDeadline < CURRENT_TIMESTAMP", Long.class)
                                .getSingleResult() > 0;
        boolean anyWarning = !anyBreached && em.createQuery(
                                                       "SELECT COUNT(e) FROM IncidentEntity e WHERE e.status <> 'CLOSED' AND e.claimDeadline < CURRENT_TIMESTAMP", Long.class)
                                               .getSingleResult() > 0;

        String slaStatus = anyBreached ? "BREACHED" : anyWarning ? "WARNING" : "OK";

        return new IncidentSummary(totalActive, slaStatus, bySeverity);
    }
}
