package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentSeverityDescriptorTest {

    @Test
    void critical_hasFourDecompositionSteps() {
        var desc = IncidentSeverityDescriptor.forSeverity(IncidentSeverity.CRITICAL);
        assertEquals(List.of("emergency-halt", "close-positions", "alert-oncall", "verify"),
                desc.decompositionSteps());
    }

    @Test
    void critical_hasTwoMinClaimDeadline() {
        var desc = IncidentSeverityDescriptor.forSeverity(IncidentSeverity.CRITICAL);
        assertEquals(Duration.ofMinutes(2), desc.claimDeadline());
        assertEquals(Duration.ofMinutes(5), desc.completionDeadline());
    }

    @Test
    void high_hasFourDecompositionSteps() {
        var desc = IncidentSeverityDescriptor.forSeverity(IncidentSeverity.HIGH);
        assertEquals(List.of("reduce-exposure", "hedge", "alert-oncall", "verify"),
                desc.decompositionSteps());
    }

    @Test
    void high_hasSevenMinClaimDeadline() {
        var desc = IncidentSeverityDescriptor.forSeverity(IncidentSeverity.HIGH);
        assertEquals(Duration.ofMinutes(7), desc.claimDeadline());
        assertEquals(Duration.ofMinutes(15), desc.completionDeadline());
    }

    @Test
    void medium_hasThreeDecompositionSteps() {
        var desc = IncidentSeverityDescriptor.forSeverity(IncidentSeverity.MEDIUM);
        assertEquals(List.of("adjust-limits", "monitor", "verify"),
                desc.decompositionSteps());
    }

    @Test
    void medium_hasThirtyMinClaimDeadline() {
        var desc = IncidentSeverityDescriptor.forSeverity(IncidentSeverity.MEDIUM);
        assertEquals(Duration.ofMinutes(30), desc.claimDeadline());
        assertEquals(Duration.ofMinutes(60), desc.completionDeadline());
    }

    @ParameterizedTest
    @EnumSource(IncidentSeverity.class)
    void allSeverities_haveDescriptors(IncidentSeverity severity) {
        var desc = IncidentSeverityDescriptor.forSeverity(severity);
        assertNotNull(desc);
        assertEquals(severity, desc.severity());
        assertFalse(desc.decompositionSteps().isEmpty());
        assertNotNull(desc.claimDeadline());
        assertNotNull(desc.completionDeadline());
        assertFalse(desc.candidateGroups().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(IncidentSeverity.class)
    void allSeverities_lastStepIsVerify(IncidentSeverity severity) {
        var desc = IncidentSeverityDescriptor.forSeverity(severity);
        var steps = desc.decompositionSteps();
        assertEquals("verify", steps.get(steps.size() - 1));
    }

    @ParameterizedTest
    @EnumSource(IncidentSeverity.class)
    void claimDeadline_alwaysShorterThanCompletion(IncidentSeverity severity) {
        var desc = IncidentSeverityDescriptor.forSeverity(severity);
        assertTrue(desc.claimDeadline().compareTo(desc.completionDeadline()) < 0);
    }
}
