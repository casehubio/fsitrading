package io.casehub.fsitrading.app.situation;

import io.casehub.platform.api.expression.LambdaExpression;
import io.casehub.ras.api.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

@ApplicationScoped
public class FsiTradingSituationDefinitionProvider implements SituationDefinitionProvider {

    public static final String VOLATILITY_SPIKE = "fsitrading.volatility-spike";
    public static final String MARKET_ANOMALY = "fsitrading.market-anomaly";
    public static final String ACTIVE_BREACH = "fsitrading.active-breach";

    @Override
    public List<GanglionDescriptor> ganglionDescriptors() {
        return List.of(
                ganglion("volatile-detected", FsiTradingEventTypes.CONDITION, ctx -> {
                    var d = data(ctx);
                    return d != null && "VOLATILE".equals(d.get("to"));
                }, 0.8),
                ganglion("anomalous-detected", FsiTradingEventTypes.CONDITION, ctx -> {
                    var d = data(ctx);
                    return d != null && "ANOMALOUS".equals(d.get("to"));
                }, 0.95),
                ganglion("stable-detected", FsiTradingEventTypes.CONDITION, ctx -> {
                    var d = data(ctx);
                    return d != null && "STABLE".equals(d.get("to"));
                }, 0.9),
                ganglion("breach-signal", FsiTradingEventTypes.SECURITY, ctx -> {
                    var d = data(ctx);
                    return d != null && "BREACH".equals(d.get("category"));
                }, 0.99));
    }

    @Override
    public List<SituationRegistration> registrations() {
        return List.of(
                new SituationRegistration(
                        new SituationDefinition(
                                VOLATILITY_SPIKE,
                                Set.of(FsiTradingEventTypes.CONDITION),
                                Duration.ofMinutes(30),
                                null,
                                new ChainMode.Count("volatile-detected", 1),
                                new TriggerAction.NotifyOnly(),
                                new TriggerMode.Repeating(Duration.ofMinutes(5))),
                        null),
                new SituationRegistration(
                        new SituationDefinition(
                                MARKET_ANOMALY,
                                Set.of(FsiTradingEventTypes.CONDITION),
                                Duration.ofMinutes(15),
                                null,
                                new ChainMode.Count("anomalous-detected", 1),
                                new TriggerAction.NotifyOnly(),
                                new TriggerMode.Repeating(Duration.ofMinutes(2))),
                        null),
                new SituationRegistration(
                        new SituationDefinition(
                                ACTIVE_BREACH,
                                Set.of(FsiTradingEventTypes.SECURITY),
                                Duration.ofHours(2),
                                null,
                                new ChainMode.Count("breach-signal", 1),
                                new TriggerAction.CreateCase(
                                        new CaseTriggerConfig("fsitrading", "overnight-incident",
                                                "1.0", Map.of())),
                                new TriggerMode.FireOnce()),
                        null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map ctx) {
        Object d = ctx.get("data");
        return d instanceof Map ? (Map<String, Object>) d : null;
    }

    @SuppressWarnings("unchecked")
    private static GanglionDescriptor ganglion(String id, String eventType,
                                                Function<Map, Boolean> condition,
                                                double confidence) {
        return new GanglionDescriptor.ExpressionRules(
                id,
                Set.of(eventType),
                List.of(new GanglionDescriptor.ExpressionRules.Rule(
                        new LambdaExpression<>(condition),
                        DetectionSignal.DETECTED,
                        confidence,
                        null,
                        Map.of())),
                Map.of());
    }
}
