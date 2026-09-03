package io.casehub.fsitrading.app.gdpr;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.platform.api.identity.TenancyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GdprErasureResourceTest {

    private FsiGdprErasureService erasureService;
    private GdprErasureResource resource;

    @BeforeEach
    void setUp() {
        erasureService = mock(FsiGdprErasureService.class);
        resource = new GdprErasureResource();
        resource.erasureService = erasureService;
    }

    @Test
    void postErasureReturnsResult() {
        var ledgerResult = new LedgerErasureService.ErasureResult(
            "trader-1", true, 5, Optional.of(UUID.randomUUID()));
        when(erasureService.erase("trader-1", TenancyConstants.DEFAULT_TENANT_ID,
            ErasureReason.GDPR_ART_17_REQUEST))
            .thenReturn(new FsiErasureResult("trader-1", 3, 2, ledgerResult));

        var request = new GdprErasureResource.ErasureRequest("trader-1",
            ErasureReason.GDPR_ART_17_REQUEST);
        Response response = resource.erase(request);

        assertThat(response.getStatus()).isEqualTo(200);
        FsiErasureResult result = (FsiErasureResult) response.getEntity();
        assertThat(result.memoriesErased()).isEqualTo(3);
        assertThat(result.cbrCasesErased()).isEqualTo(2);
    }

    @Test
    void nullRequestReturnsBadRequest() {
        Response response = resource.erase(null);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void missingTraderIdReturnsBadRequest() {
        var request = new GdprErasureResource.ErasureRequest(null,
            ErasureReason.GDPR_ART_17_REQUEST);
        Response response = resource.erase(request);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void blankTraderIdReturnsBadRequest() {
        var request = new GdprErasureResource.ErasureRequest("  ",
            ErasureReason.GDPR_ART_17_REQUEST);
        Response response = resource.erase(request);
        assertThat(response.getStatus()).isEqualTo(400);
    }
}
