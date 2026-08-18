package io.casehub.fsitrading.app.resource;

import io.casehub.pages.layout.LayoutPersistenceStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/layout")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class LayoutResource {

    @Inject
    LayoutPersistenceStore layoutStore;

    @GET
    @Path("/{key}")
    public Response get(@PathParam("key") String key) {
        return layoutStore.load(key, "default", "default")
                .map(data -> Response.ok(data).build())
                .orElse(Response.status(404).build());
    }

    @PUT
    @Path("/{key}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(@PathParam("key") String key, String body) {
        layoutStore.save(key, "default", "default", body);
        return Response.noContent().build();
    }
}
