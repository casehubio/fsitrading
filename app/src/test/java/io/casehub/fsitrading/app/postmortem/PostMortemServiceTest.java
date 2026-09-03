package io.casehub.fsitrading.app.postmortem;

import io.casehub.blocks.conversation.ConversationPoint;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.ConvergenceSignal;
import io.casehub.blocks.conversation.ConvergenceState;
import io.casehub.blocks.conversation.PointClassification;
import io.casehub.blocks.conversation.Priority;
import io.casehub.blocks.conversation.ThreadEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostMortemServiceTest {

    private final PostMortemService service = new PostMortemService();

    @Test
    void rendersConversationStateToMarkdown() {
        ConversationState state = new ConversationState(
            Map.of("p1", new ConversationPoint(
                "p1", "DETECTED",
                new PointClassification(Priority.HIGH, "incident-response", "AAPL"),
                List.of(
                    new ThreadEntry("e1", null, null, "bridge", Instant.now(),
                        "bridge", 1, "PROPOSE", "Agent assigned: evaluate"),
                    new ThreadEntry("e2", null, null, "agent-risk", Instant.now(),
                        "agent-risk", 1, "COMMIT", "Positions reduced")),
                "RESOLVED")),
            List.of(), List.of(), Map.of());

        ConvergenceSignal convergence = new ConvergenceSignal(
            ConvergenceState.CONSENSUS, 1.0, "incident resolved");

        String markdown = service.renderFromState(state, convergence);

        assertThat(markdown).contains("Conversation Summary");
        assertThat(markdown).contains("DETECTED");
        assertThat(markdown).contains("CONSENSUS");
        assertThat(markdown).contains("agent-risk");
        assertThat(markdown).contains("Positions reduced");
    }

    @Test
    void rendersEscalatedPointWithCorrectEmoji() {
        ConversationState state = new ConversationState(
            Map.of("p1", new ConversationPoint(
                "p1", "RESPONDED",
                new PointClassification(Priority.HIGH, "incident-response", null),
                List.of(
                    new ThreadEntry("e1", null, null, "agent-hedge", Instant.now(),
                        "agent-hedge", 1, "ASSERT", "Hedge failed")),
                "ESCALATED")),
            List.of(), List.of(), Map.of());

        ConvergenceSignal convergence = new ConvergenceSignal(
            ConvergenceState.CONVERGING, 0.5, "partial resolution");

        String markdown = service.renderFromState(state, convergence);

        assertThat(markdown).contains("🔴");
        assertThat(markdown).contains("Hedge failed");
    }

    @Test
    void rendersEmptyStateWithoutError() {
        ConversationState state = new ConversationState(
            Map.of(), List.of(), List.of(), Map.of());

        ConvergenceSignal convergence = new ConvergenceSignal(
            ConvergenceState.PROGRESSING, 0.0, "no data");

        String markdown = service.renderFromState(state, convergence);

        assertThat(markdown).contains("Conversation Summary");
    }
}
