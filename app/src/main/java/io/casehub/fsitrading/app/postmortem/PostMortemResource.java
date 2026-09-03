package io.casehub.fsitrading.app.postmortem;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/postmortem")
public class PostMortemResource {

    @Inject PostMortemService postMortemService;

    @GET
    @Path("/{caseId}")
    @Produces("text/markdown")
    public Response getPostMortem(@PathParam("caseId") UUID caseId) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("Post-mortem generation requires qhorus channel replay — not yet wired").build();
    }
}
