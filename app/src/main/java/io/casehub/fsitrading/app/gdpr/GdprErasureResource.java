package io.casehub.fsitrading.app.gdpr;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/gdpr")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("fsi-ops")
public class GdprErasureResource {

    public record ErasureRequest(String subjectId, ErasureReason reason) {}

    @Inject FsiGdprErasureService erasureService;

    @POST
    @Path("/erase")
    public Response erase(ErasureRequest request) {
        if (request == null || request.subjectId() == null || request.subjectId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("subjectId is required").build();
        }
        FsiErasureResult result = erasureService.erase(
            request.subjectId(),
            TenancyConstants.DEFAULT_TENANT_ID,
            request.reason());
        return Response.ok(result).build();
    }
}
