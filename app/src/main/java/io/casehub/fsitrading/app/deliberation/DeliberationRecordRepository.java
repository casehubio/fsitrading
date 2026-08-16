package io.casehub.fsitrading.app.deliberation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DeliberationRecordRepository {

    @Inject
    EntityManager em;

    @Transactional
    public void persist(DeliberationRecord record) {
        em.persist(record);
    }

    @Transactional
    public DeliberationRecord merge(DeliberationRecord record) {
        return em.merge(record);
    }

    public Optional<DeliberationRecord> findById(UUID id) {
        return Optional.ofNullable(em.find(DeliberationRecord.class, id));
    }

    public List<DeliberationRecord> findByInstrumentAndStatus(String instrument, String status) {
        return em.createQuery(
                "SELECT d FROM DeliberationRecord d WHERE d.instrument = :instrument AND d.status = :status",
                DeliberationRecord.class)
                .setParameter("instrument", instrument)
                .setParameter("status", status)
                .getResultList();
    }

    public Optional<DeliberationRecord> findInProgress(String instrument) {
        var results = findByInstrumentAndStatus(instrument, "IN_PROGRESS");
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<DeliberationRecord> findAll() {
        return em.createQuery("SELECT d FROM DeliberationRecord d ORDER BY d.startedAt DESC",
                DeliberationRecord.class)
                .getResultList();
    }
}
