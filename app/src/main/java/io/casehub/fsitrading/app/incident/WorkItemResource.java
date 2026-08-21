package io.casehub.fsitrading.app.incident;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/work-items")
@Produces(MediaType.APPLICATION_JSON)
public class WorkItemResource {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    WorkItemService workItemService;

    @GET
    public List<WorkItem> list(@QueryParam("type") String type,
                               @QueryParam("candidateGroup") String candidateGroup,
                               @QueryParam("status") String status) {
        var builder = WorkItemQuery.builder();
        if (type != null) {
            builder.type(type);
        }
        if (candidateGroup != null) {
            builder.candidateGroups(List.of(candidateGroup));
        }
        if (status != null) {
            builder.status(io.casehub.work.api.WorkItemStatus.valueOf(status));
        }
        return workItemStore.scan(builder.build());
    }

    @POST
    @Path("/{id}/resolve")
    public Response resolve(@PathParam("id") UUID id, ResolveRequest request) {
        if (request.outcome() == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        WorkItem result = switch (request.outcome()) {
            case "APPROVED" -> workItemService.complete(id, request.actorId(),
                                                        request.resolution(), request.outcome());
            case "REJECTED" -> workItemService.reject(id, request.actorId(),
                                                      request.resolution(), request.outcome());
            case "DELEGATED" -> workItemService.delegate(id, request.actorId(),
                                                         request.delegateTo(), null);
            default -> throw new IllegalArgumentException("Unknown outcome: " + request.outcome());
        };
        return Response.ok(result).build();}

    public record ResolveRequest(
            String actorId,
            String outcome,
            String resolution,
            String delegateTo) {}
}
