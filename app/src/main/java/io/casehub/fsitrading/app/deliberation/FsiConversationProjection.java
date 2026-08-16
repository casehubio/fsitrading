package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.conversation.ConversationProjection;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FsiConversationProjection extends ConversationProjection {

    @Override
    protected String sentinel() {
        return "##FSI##";
    }

    @Override
    protected boolean isPointInitiator(String entryType) {
        return "RAISE".equals(entryType) || "PROPOSE".equals(entryType);
    }

    @Override
    protected String statusAfter(String entryType) {
        return switch (entryType) {
            case "AGREE" -> "AGREED";
            case "COUNTER" -> "ACTIVE";
            case "DISPUTE" -> "DISPUTED";
            case "QUALIFY" -> "ACTIVE";
            case "RESOLVE" -> "RESOLVED";
            default -> null;
        };
    }
}
