package io.casehub.fsitrading.app.compliance;

import io.casehub.api.spi.routing.RequirementStatus;
import io.casehub.api.spi.routing.TrustRoutingRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceStatusRecordTest {

    @Test
    void mapsCitationToRegulationAndRequirement() {
        var req = new TrustRoutingRequirement(
                "MIFID2_ART17",
                "MiFID II Article 17 — algorithmic trading decision audit",
                "Tamper-evident ledger",
                RequirementStatus.CLOSED, List.of());

        ComplianceStatusRecord record = ComplianceStatusRecord.from(req);

        assertThat(record.regulation()).isEqualTo("MiFID II Article 17");
        assertThat(record.requirement()).isEqualTo("algorithmic trading decision audit");
        assertThat(record.mechanism()).isEqualTo("Tamper-evident ledger");
    }

    @Test
    void mapsClosedToMet() {
        var req = new TrustRoutingRequirement(
                "TEST", "Reg — Req", "Mech",
                RequirementStatus.CLOSED, List.of());

        assertThat(ComplianceStatusRecord.from(req).status()).isEqualTo("MET");
    }

    @Test
    void mapsPartialToPartial() {
        var req = new TrustRoutingRequirement(
                "TEST", "Reg — Req", "Mech",
                RequirementStatus.PARTIAL, List.of());

        assertThat(ComplianceStatusRecord.from(req).status()).isEqualTo("PARTIAL");
    }

    @Test
    void mapsGapToGap() {
        var req = new TrustRoutingRequirement(
                "TEST", "Reg — Req", "Mech",
                RequirementStatus.GAP, List.of());

        assertThat(ComplianceStatusRecord.from(req).status()).isEqualTo("GAP");
    }

    @Test
    void mapsBreachedToBreached() {
        var req = new TrustRoutingRequirement(
                "TEST", "Reg — Req", "Mech",
                RequirementStatus.BREACHED, List.of());

        assertThat(ComplianceStatusRecord.from(req).status()).isEqualTo("BREACHED");
    }

    @Test
    void citationWithoutDashUsesFullAsRegulation() {
        var req = new TrustRoutingRequirement(
                "SIMPLE_ID", "Simple citation text", "Mech",
                RequirementStatus.CLOSED, List.of());

        ComplianceStatusRecord record = ComplianceStatusRecord.from(req);

        assertThat(record.regulation()).isEqualTo("Simple citation text");
        assertThat(record.requirement()).isEqualTo("SIMPLE_ID");
    }

    @Test
    void evidenceUrlIsNull() {
        var req = new TrustRoutingRequirement(
                "TEST", "Reg — Req", "Mech",
                RequirementStatus.CLOSED, List.of());

        assertThat(ComplianceStatusRecord.from(req).evidenceUrl()).isNull();
    }
}
