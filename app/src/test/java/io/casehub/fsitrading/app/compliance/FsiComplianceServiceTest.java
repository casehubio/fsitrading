package io.casehub.fsitrading.app.compliance;

import io.casehub.api.spi.routing.RequirementStatus;
import io.casehub.api.spi.routing.TrustRoutingRequirement;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FsiComplianceServiceTest {

    private CbrCaseMemoryStore cbrCaseMemoryStore;
    private FsiComplianceService service;

    @BeforeEach
    void setUp() {
        cbrCaseMemoryStore = mock(CbrCaseMemoryStore.class);
        service = new FsiComplianceService();
        service.meterRegistry = new SimpleMeterRegistry();
        service.cbrCaseMemoryStore = cbrCaseMemoryStore;
    }

    @Test
    void evaluateAllReturnsFiveRequirements() {
        List<TrustRoutingRequirement> requirements = service.evaluateAll();

        assertThat(requirements).hasSize(5);
        assertThat(requirements).extracting(TrustRoutingRequirement::requirementId)
            .containsExactlyInAnyOrder(
                "MIFID2_ART17", "MIFID2_RTS6_MON", "MIFID2_RTS6_KILL",
                "DODD_FRANK_AUDIT", "MAR_SURVEILLANCE");
    }

    @Test
    void mifidArt17ClosedWhenLedgerTypesExist() {
        TrustRoutingRequirement mifid = service.evaluateMifidArt17();
        assertThat(mifid.status()).isEqualTo(RequirementStatus.CLOSED);
    }

    @Test
    void rts6MonitoringGapWhenNoMetersRegistered() {
        TrustRoutingRequirement rts6 = service.evaluateRts6Monitoring();
        assertThat(rts6.status()).isEqualTo(RequirementStatus.GAP);
    }

    @Test
    void rts6MonitoringClosedWhenMetersExist() {
        service.meterRegistry.counter("strategy.evaluation.count");
        TrustRoutingRequirement rts6 = service.evaluateRts6Monitoring();
        assertThat(rts6.status()).isEqualTo(RequirementStatus.CLOSED);
    }

    @Test
    void doddFrankClosedWhenLedgerServiceExists() {
        TrustRoutingRequirement doddFrank = service.evaluateDoddFrankAudit();
        assertThat(doddFrank.status()).isEqualTo(RequirementStatus.CLOSED);
    }

    @Test
    void marSurveillancePartialWhenNoCbrCases() {
        when(cbrCaseMemoryStore.scan(any(CbrScanRequest.class)))
            .thenReturn(new CbrScanResult(List.of(), null));
        TrustRoutingRequirement mar = service.evaluateMarSurveillance();
        assertThat(mar.status()).isEqualTo(RequirementStatus.PARTIAL);
    }

    @Test
    void eachRequirementHasRequiredFields() {
        List<TrustRoutingRequirement> requirements = service.evaluateAll();
        for (TrustRoutingRequirement req : requirements) {
            assertThat(req.requirementId()).isNotBlank();
            assertThat(req.citation()).isNotBlank();
            assertThat(req.mechanism()).isNotBlank();
            assertThat(req.status()).isNotNull();
            assertThat(req.decisions()).isNotNull();
        }
    }
}
