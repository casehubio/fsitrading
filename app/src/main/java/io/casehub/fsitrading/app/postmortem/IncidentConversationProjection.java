package io.casehub.fsitrading.app.postmortem;

import io.casehub.blocks.conversation.ConversationProjection;

public class IncidentConversationProjection extends ConversationProjection {

    @Override
    protected String sentinel() {
        return "PMETA:";
    }

    @Override
    protected boolean isPointInitiator(String entryType) {
        return "PROPOSE".equals(entryType);
    }

    @Override
    protected String statusAfter(String entryType) {
        return switch (entryType) {
            case "DONE" -> "RESOLVED";
            case "ASSERT" -> "ESCALATED";
            default -> "OPEN";
        };
    }
}
