package io.casehub.fsitrading.app.narrative;

import io.casehub.blocks.summarisation.narrative.DecisionNarrative;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Path("/api/narrative")
@Produces(MediaType.APPLICATION_JSON)
public class NarrativeResource {

    private final Map<String, List<DecisionNarrative>> narrativeCache = new ConcurrentHashMap<>();

    @Inject
    public NarrativeResource(DecisionNarrativePipeline pipeline) {
        pipeline.narrativeBus().subscribe(e -> true, event -> {
            var narrative = event.payload();
            narrativeCache.computeIfAbsent(narrative.caseId(), k -> new CopyOnWriteArrayList<>())
                    .add(narrative);
        });
    }

    @GET
    @Path("/{caseId}")
    public List<DecisionNarrative> get(@PathParam("caseId") String caseId) {
        return narrativeCache.getOrDefault(caseId, List.of());
    }
}
