package io.casehub.fsitrading.app.incident;

import io.casehub.eidos.org.api.AgentRelationship;
import io.casehub.eidos.org.api.OrgRegistry;
import io.casehub.eidos.org.api.RelationshipKind;
import io.casehub.eidos.org.memory.InMemoryOrgRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FsiOrgStructureTest {

    private OrgRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryOrgRegistry();
        FsiOrgStructureLoader.load(registry);
    }

    @Test
    void emergencyResponseHasFiveMembers() {
        var members = registry.membersOf("emergency-response", FsiOrgStructureLoader.TENANT);
        assertThat(members).hasSize(5);
    }

    @Test
    void riskManagementHasThreeMembers() {
        var members = registry.membersOf("risk-management", FsiOrgStructureLoader.TENANT);
        assertThat(members).hasSize(3);
    }

    @Test
    void analysisHasThreeMembers() {
        var members = registry.membersOf("analysis", FsiOrgStructureLoader.TENANT);
        assertThat(members).hasSize(3);
    }

    @Test
    void operationsHasTwoMembers() {
        var members = registry.membersOf("operations", FsiOrgStructureLoader.TENANT);
        assertThat(members).hasSize(2);
    }

    @Test
    void totalThirteenAgents() {
        int total = registry.membersOf("emergency-response", FsiOrgStructureLoader.TENANT).size()
            + registry.membersOf("risk-management", FsiOrgStructureLoader.TENANT).size()
            + registry.membersOf("analysis", FsiOrgStructureLoader.TENANT).size()
            + registry.membersOf("operations", FsiOrgStructureLoader.TENANT).size();
        assertThat(total).isEqualTo(13);
    }

    @Test
    void escalationPathFromAnalysisLeadToEmergencyLead() {
        var path = registry.escalationPath("sentimentAnalyserAgent", FsiOrgStructureLoader.TENANT);
        assertThat(path).extracting(AgentRelationship::targetAgentId)
            .containsExactly("positionReducerAgent", "emergencyHaltAgent");
    }

    @Test
    void verifyAgentSupervisesAllTeamLeads() {
        var subordinates = registry.subordinates("verifyAgent", FsiOrgStructureLoader.TENANT);
        assertThat(subordinates).extracting(AgentRelationship::targetAgentId)
            .containsExactlyInAnyOrder(
                "sentimentAnalyserAgent", "positionReducerAgent", "emergencyHaltAgent");
    }

    @Test
    void withinTeamReportingToLead() {
        var rels = registry.relationshipsFrom("hedgeAgent", FsiOrgStructureLoader.TENANT);
        assertThat(rels).anyMatch(r ->
                                          r.targetAgentId().equals("positionReducerAgent")
                                          && r.kind() == RelationshipKind.REPORTS_TO);
    }

    @Test
    void tradingDeskHasFourChildUnits() {
        var children = registry.childUnits("trading-desk", FsiOrgStructureLoader.TENANT);
        assertThat(children).hasSize(4);
    }

    @Test
    void agentBelongsToUnit() {
        var units = registry.unitsFor("monitorAgent", FsiOrgStructureLoader.TENANT);
        assertThat(units).anyMatch(u -> u.unitId().equals("analysis"));
    }

    @Test
    void emergencyResponseCapabilitiesHaveFastTier() {
        var unit = registry.findUnit("emergency-response", FsiOrgStructureLoader.TENANT).orElseThrow();
        assertThat(unit.capabilities()).allMatch(c -> "tier:FAST".equals(c.modelRef()));
    }

    @Test
    void analysisHasFlagshipForSentimentAnalysis() {
        var unit = registry.findUnit("analysis", FsiOrgStructureLoader.TENANT).orElseThrow();
        assertThat(unit.capabilities())
                .anyMatch(c -> "sentiment-analysis".equals(c.name()) && "tier:FLAGSHIP".equals(c.modelRef()));
    }

}
