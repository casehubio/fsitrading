package io.casehub.fsitrading.app.postmortem;

import io.casehub.blocks.conversation.ConversationRendererConfig;

import java.util.Map;
import java.util.Set;

public final class FsiConversationRendererConfig {

    private FsiConversationRendererConfig() {}

    public static ConversationRendererConfig create() {
        return ConversationRendererConfig.builder()
            .groupByTopic(true)
            .showEpistemicStatus(true)
            .showConvergenceSignal(true)
            .showObligationChain(true)
            .showProgress(false)
            .statusEmoji(Map.of(
                "OPEN", "🟡",
                "RESOLVED", "✅",
                "ESCALATED", "🔴"))
            .resolvedStatuses(Set.of("RESOLVED"))
            .escalatedStatuses(Set.of("ESCALATED"))
            .build();
    }
}
