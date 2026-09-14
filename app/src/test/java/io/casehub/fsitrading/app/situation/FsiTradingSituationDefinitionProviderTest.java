package io.casehub.fsitrading.app.situation;

import io.casehub.ras.api.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FsiTradingSituationDefinitionProviderTest {

    private final FsiTradingSituationDefinitionProvider provider =
            new FsiTradingSituationDefinitionProvider();

    @Test
    void providesFourGanglia() {
        assertThat(provider.ganglionDescriptors()).hasSize(4);
    }

    @Test
    void ganglionIdsAreCorrect() {
        var ids = provider.ganglionDescriptors().stream()
                .map(GanglionDescriptor::ganglionId)
                .toList();
        assertThat(ids).containsExactly(
                "volatile-detected", "anomalous-detected",
                "stable-detected", "breach-signal");
    }

    @Test
    void ganglionEventTypesMatchConstants() {
        var ganglia = provider.ganglionDescriptors();
        assertThat(ganglia.get(0).handledEventTypes())
                .isEqualTo(Set.of(FsiTradingEventTypes.CONDITION));
        assertThat(ganglia.get(1).handledEventTypes())
                .isEqualTo(Set.of(FsiTradingEventTypes.CONDITION));
        assertThat(ganglia.get(2).handledEventTypes())
                .isEqualTo(Set.of(FsiTradingEventTypes.CONDITION));
        assertThat(ganglia.get(3).handledEventTypes())
                .isEqualTo(Set.of(FsiTradingEventTypes.SECURITY));
    }

    @Test
    void providesThreeRegistrations() {
        assertThat(provider.registrations()).hasSize(3);
    }

    @Test
    void registrationSituationIdsMatchConstants() {
        var ids = provider.registrations().stream()
                .map(r -> r.definition().situationId())
                .toList();
        assertThat(ids).containsExactly(
                FsiTradingSituationDefinitionProvider.VOLATILITY_SPIKE,
                FsiTradingSituationDefinitionProvider.MARKET_ANOMALY,
                FsiTradingSituationDefinitionProvider.ACTIVE_BREACH);
    }

    @Test
    void volatilitySpikeUsesRepeatingMode() {
        var def = provider.registrations().get(0).definition();
        assertThat(def.triggerMode()).isInstanceOf(TriggerMode.Repeating.class);
        assertThat(((TriggerMode.Repeating) def.triggerMode()).cooldown())
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void activeBreachCreatesCase() {
        var def = provider.registrations().get(2).definition();
        assertThat(def.triggerAction()).isInstanceOf(TriggerAction.CreateCase.class);
        var config = ((TriggerAction.CreateCase) def.triggerAction()).config();
        assertThat(config.caseNamespace()).isEqualTo("fsitrading");
        assertThat(config.caseName()).isEqualTo("overnight-incident");
    }

    @Test
    void activeBreachUsesFireOnceMode() {
        var def = provider.registrations().get(2).definition();
        assertThat(def.triggerMode()).isInstanceOf(TriggerMode.FireOnce.class);
    }

    @Test
    void correlationWindowsAreSet() {
        var regs = provider.registrations();
        assertThat(regs.get(0).definition().correlationWindow())
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(regs.get(1).definition().correlationWindow())
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(regs.get(2).definition().correlationWindow())
                .isEqualTo(Duration.ofHours(2));
    }
}
