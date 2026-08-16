package io.casehub.fsitrading.app.deliberation;

import io.casehub.fsitrading.model.RegimeChanged;
import io.casehub.fsitrading.model.TrendReversalDetected;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.function.Consumer;

@ApplicationScoped
public class FsiDeliberationTrigger {

    private static final Logger log = Logger.getLogger(FsiDeliberationTrigger.class);

    @Inject
    DeliberationRecordRepository repository;

    @Inject
    EntityManager em;

    @ConfigProperty(name = "fsi.deliberation.trend-reversal-threshold", defaultValue = "0.5")
    double trendReversalThreshold;

    private Consumer<String> deliberationStarter;

    public void setDeliberationStarter(Consumer<String> starter) {
        this.deliberationStarter = starter;
    }

    public Consumer<TrendReversalDetected> trendReversalConsumer() {
        return this::onTrendReversal;
    }

    public Consumer<RegimeChanged> regimeChangeConsumer() {
        return this::onRegimeChange;
    }

    void onTrendReversal(TrendReversalDetected event) {
        if (Math.abs(event.trendSummary().momentum()) <= trendReversalThreshold) {
            log.debugf("Trend reversal for %s below threshold (momentum=%.3f)",
                    event.instrument(), event.trendSummary().momentum());
            return;
        }
        triggerIfNotInProgress(event.instrument(), "TREND_REVERSAL");
    }

    void onRegimeChange(RegimeChanged event) {
        triggerIfNotInProgress(event.instrument(), "REGIME_CHANGED");
    }

    private void triggerIfNotInProgress(String instrument, String triggerType) {
        if (repository.findInProgress(instrument).isPresent()) {
            log.debugf("Deliberation already in progress for %s — skipping %s trigger",
                    instrument, triggerType);
            return;
        }
        log.infof("Triggering deliberation for %s (type=%s)", instrument, triggerType);
        if (deliberationStarter != null) {
            deliberationStarter.accept(instrument);
        }
    }

    @Transactional
    public void recoverFromCrash() {
        int updated = em.createQuery(
                "UPDATE DeliberationRecord d SET d.status = 'FAILED', d.endedAt = :now, d.summary = 'server_restart_recovery' WHERE d.status = 'IN_PROGRESS'")
                .setParameter("now", java.time.Instant.now())
                .executeUpdate();
        if (updated > 0) {
            log.infof("Crash recovery: marked %d IN_PROGRESS deliberations as FAILED", updated);
        }
    }
}
