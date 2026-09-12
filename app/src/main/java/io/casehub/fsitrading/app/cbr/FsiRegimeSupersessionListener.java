package io.casehub.fsitrading.app.cbr;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.ResolvedCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class FsiRegimeSupersessionListener {

    private static final Logger LOG = Logger.getLogger(FsiRegimeSupersessionListener.class);
    private static final MemoryDomain FSI_DOMAIN = new MemoryDomain("fsitrading");

    private final CbrCaseMemoryStore cbrStore;
    private final Map<String, MarketRegime> lastRegime = new ConcurrentHashMap<>();

    @Inject
    public FsiRegimeSupersessionListener(CbrCaseMemoryStore cbrStore) {
        this.cbrStore = cbrStore;
    }

    public void wire(EventStreamBus<RegimeAssessment> l3Bus) {
        l3Bus.subscribe(e -> true, this::onRegimeAssessment);
    }

    private void onRegimeAssessment(LevelEvent<RegimeAssessment> event) {
        RegimeAssessment assessment = event.payload();
        MarketRegime previous = lastRegime.put(assessment.instrument(), assessment.regime());

        if (previous != null && previous != assessment.regime()) {
            String tenantId = event.tenancyId() != null ? event.tenancyId() : "default";
            int count = cbrStore.supersedeMatching(
                    tenantId, FSI_DOMAIN, ResolvedCase.CBR_TYPE,
                    Map.of("market_regime", CbrFilter.contains(previous.name())),
                    "Regime changed from " + previous + " to " + assessment.regime()
                            + " on " + assessment.instrument());
            LOG.infof("Regime %s → %s on %s: superseded %d CBR cases",
                    previous, assessment.regime(), assessment.instrument(), count);
        }
    }
}
