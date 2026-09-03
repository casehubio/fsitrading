package io.casehub.fsitrading.app.postmortem;

import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.blocks.conversation.ConversationState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentConversationProjectionTest {

    private final IncidentConversationProjection projection = new IncidentConversationProjection();

    @Test
    void sentinelEncodesParsesCorrectly() {
        String encoded = ChannelMessageMeta.encode("PMETA:",
            Map.of("entryType", "PROPOSE", "role", "bridge"),
            "Agent risk-monitor assigned: evaluate positions");

        Map<String, String> parsed = ChannelMessageMeta.parseMeta("PMETA:", encoded);
        assertThat(parsed.get("entryType")).isEqualTo("PROPOSE");
        assertThat(parsed.get("role")).isEqualTo("bridge");
    }

    @Test
    void identityReturnsEmptyState() {
        ConversationState empty = projection.identity();
        assertThat(empty.points()).isEmpty();
        assertThat(empty.humanFlags()).isEmpty();
        assertThat(empty.memos()).isEmpty();
    }
}
