package io.casehub.fsitrading.app.postmortem;

import io.casehub.agency.deliberation.conversation.ConversationPoint;
import io.casehub.agency.deliberation.conversation.EpistemicRule;
import io.casehub.agency.deliberation.conversation.EpistemicStatus;
import io.casehub.agency.deliberation.conversation.ParticipantContext;

public final class FsiEpistemicRule implements EpistemicRule {

    public static final FsiEpistemicRule INSTANCE = new FsiEpistemicRule();

    private FsiEpistemicRule() {}

    @Override
    public EpistemicStatus classify(ConversationPoint point, ParticipantContext context) {
        boolean hasAssert = point.thread().stream()
            .anyMatch(e -> "ASSERT".equals(e.entryType()));
        if (hasAssert) return EpistemicStatus.DISPUTED;

        boolean hasCommitOrDone = point.thread().stream()
            .anyMatch(e -> "COMMIT".equals(e.entryType()) || "DONE".equals(e.entryType()));
        if (hasCommitOrDone) return EpistemicStatus.ESTABLISHED;

        return EpistemicStatus.PENDING;
    }
}
