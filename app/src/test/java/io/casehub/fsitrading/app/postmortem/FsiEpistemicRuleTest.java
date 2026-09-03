package io.casehub.fsitrading.app.postmortem;

import io.casehub.blocks.conversation.ConversationPoint;
import io.casehub.blocks.conversation.EpistemicStatus;
import io.casehub.blocks.conversation.ParticipantContext;
import io.casehub.blocks.conversation.PointClassification;
import io.casehub.blocks.conversation.Priority;
import io.casehub.blocks.conversation.ThreadEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FsiEpistemicRuleTest {

    private static final ParticipantContext EMPTY_CTX = new ParticipantContext(
        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0);

    @Test
    void establishedWhenAllEntriesAreCommitOrDone() {
        ConversationPoint point = new ConversationPoint(
            "p1", "DETECTED",
            new PointClassification(Priority.HIGH, "incident-response", null),
            List.of(
                new ThreadEntry("e1", null, null, "agent-1", Instant.now(),
                    "agent-1", 1, "COMMIT", "Positions reduced"),
                new ThreadEntry("e2", null, null, "bridge", Instant.now(),
                    "bridge", 1, "DONE", "Goal met")),
            "RESOLVED");

        EpistemicStatus status = FsiEpistemicRule.INSTANCE.classify(point, EMPTY_CTX);
        assertThat(status).isEqualTo(EpistemicStatus.ESTABLISHED);
    }

    @Test
    void disputedWhenAnyAssert() {
        ConversationPoint point = new ConversationPoint(
            "p1", "RESPONDED",
            new PointClassification(Priority.HIGH, "incident-response", null),
            List.of(
                new ThreadEntry("e1", null, null, "agent-1", Instant.now(),
                    "agent-1", 1, "COMMIT", "Hedge placed"),
                new ThreadEntry("e2", null, null, "agent-2", Instant.now(),
                    "agent-2", 1, "ASSERT", "Hedge failed: insufficient liquidity")),
            "ESCALATED");

        EpistemicStatus status = FsiEpistemicRule.INSTANCE.classify(point, EMPTY_CTX);
        assertThat(status).isEqualTo(EpistemicStatus.DISPUTED);
    }

    @Test
    void pendingWhenOnlyProposeEntries() {
        ConversationPoint point = new ConversationPoint(
            "p1", "CLASSIFIED",
            new PointClassification(Priority.MEDIUM, "incident-response", null),
            List.of(
                new ThreadEntry("e1", null, null, "bridge", Instant.now(),
                    "bridge", 1, "PROPOSE", "Agent assigned")),
            "OPEN");

        EpistemicStatus status = FsiEpistemicRule.INSTANCE.classify(point, EMPTY_CTX);
        assertThat(status).isEqualTo(EpistemicStatus.PENDING);
    }
}
