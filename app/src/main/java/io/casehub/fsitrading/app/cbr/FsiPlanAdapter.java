package io.casehub.fsitrading.app.cbr;

import io.casehub.fsitrading.app.pipeline.MarketPulseState;
import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanAdapter;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FsiPlanAdapter implements PlanAdapter {

    private static final double TRUST_THRESHOLD = 0.4;
    private static final double VOLATILITY_BOOST_RATIO = 2.0;
    private static final java.util.Set<String> HIGH_SEVERITY_EVENTS =
            java.util.Set.of("FLASH_CRASH", "MARGIN_CALL", "CIRCUIT_BREAKER");

    private final AgentTrustProvider trustProvider;
    private final MarketPulseState pulseState;

    @Inject
    public FsiPlanAdapter(AgentTrustProvider trustProvider,
                          MarketPulseState pulseState) {
        this.trustProvider = trustProvider;
        this.pulseState = pulseState;
    }

    @Override
    public AdaptedPlan adapt(String caseType, ScoredCbrCase<PlanCbrCase> retrieved,
                             Map<String, FeatureValue> currentFeatures) {
        List<AdaptedStep> steps    = new ArrayList<>();
        PlanCbrCase       pastCase = retrieved.cbrCase();

        double pastVolatility    = extractNumeric(pastCase.features(), "volatility_at_detection");
        double currentVolatility = extractNumeric(currentFeatures, "volatility_at_detection");

        String eventType = extractString(currentFeatures, "event_type");
        if (HIGH_SEVERITY_EVENTS.contains(eventType)) {
            steps.add(new AdaptedStep("pre-reduce", "incident-respond",
                                      null, null, 0, Map.of(),
                                      AdaptationAction.ADDED,
                                      "High-severity event " + eventType + " — advisory pre-reduce"));
        }

        for (PlanTrace trace : pastCase.planTrace()) {
            steps.add(adaptStep(trace, pastVolatility, currentVolatility));
        }

        return new AdaptedPlan(steps);
    }

    private AdaptedStep adaptStep(PlanTrace trace, double pastVol, double currentVol) {
        if (pulseState.isMarketClosed()) {
            return step(trace, AdaptationAction.SUPPRESSED,
                    "Market closed — step requires market access");
        }

        var trust = trustProvider.currentTrustScore(trace.workerName());
        if (trust.isPresent() && trust.getAsDouble() < TRUST_THRESHOLD) {
            return new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                    null, trace.stepOutcome(), trace.priority(),
                    trace.parameters(), AdaptationAction.SUBSTITUTED,
                    "Agent trust " + trust.getAsDouble() + " below threshold " + TRUST_THRESHOLD);
        }

        if (pastVol > 0 && currentVol > pastVol * VOLATILITY_BOOST_RATIO) {
            return new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                    trace.workerName(), trace.stepOutcome(),
                    Math.max(0, trace.priority() - 1),
                    trace.parameters(), AdaptationAction.BOOSTED,
                    "Volatility " + currentVol + " > " + pastVol + " x " + VOLATILITY_BOOST_RATIO);
        }

        return step(trace, AdaptationAction.RETAINED, null);
    }

    private AdaptedStep step(PlanTrace trace, AdaptationAction action, String reason) {
        return new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                trace.workerName(), trace.stepOutcome(), trace.priority(),
                trace.parameters(), action, reason);
    }

    private double extractNumeric(Map<String, FeatureValue> features, String key) {
        FeatureValue val = features.get(key);
        if (val instanceof FeatureValue.NumberVal nv) return nv.value();
        return 0.0;
    }

    private String extractString(Map<String, FeatureValue> features, String key) {
        FeatureValue val = features.get(key);
        if (val instanceof FeatureValue.StringVal sv) {return sv.value();}
        return "";
    }

}
