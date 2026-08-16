package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FsiConversationProjectionTest {

    private final FsiConversationProjection projection = new FsiConversationProjection();

    @Test
    void sentinelIsFsi() {
        assertEquals("##FSI##", projection.sentinel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RAISE", "PROPOSE"})
    void pointInitiators(String entryType) {
        assertTrue(projection.isPointInitiator(entryType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AGREE", "COUNTER", "DISPUTE", "QUALIFY", "RESOLVE"})
    void responseTypesAreNotPointInitiators(String entryType) {
        assertFalse(projection.isPointInitiator(entryType));
    }

    @Test
    void statusAfterAgree() {
        assertEquals("AGREED", projection.statusAfter("AGREE"));
    }

    @Test
    void statusAfterCounter() {
        assertEquals("ACTIVE", projection.statusAfter("COUNTER"));
    }

    @Test
    void statusAfterDispute() {
        assertEquals("DISPUTED", projection.statusAfter("DISPUTE"));
    }

    @Test
    void statusAfterQualify() {
        assertEquals("ACTIVE", projection.statusAfter("QUALIFY"));
    }

    @Test
    void statusAfterResolve() {
        assertEquals("RESOLVED", projection.statusAfter("RESOLVE"));
    }

    @Test
    void statusAfterUnknown() {
        assertNull(projection.statusAfter("UNKNOWN"));
    }

    @Test
    void applyRaiseCreatesPoint() {
        var state = projection.identity();
        var pointId = UUID.randomUUID().toString();
        var content = ChannelMessageMeta.encode("##FSI##",
                Map.of("entryType", "RAISE", "role", "momentum-strategy", "round", "1"),
                "AAPL showing momentum exhaustion");
        var message = new MessageView(1L, UUID.randomUUID(), "rule:momentum@v1",
                MessageType.COMMAND, content, pointId, null, null,
                "debate", List.of(), null, Instant.now(), null, 0);

        var next = projection.apply(state, message);

        assertEquals(1, next.points().size());
        var point = next.points().get(pointId);
        assertNotNull(point);
        assertEquals("OPEN", point.status());
    }

    @Test
    void applyAgreeChangesStatusToAgreed() {
        var state = projection.identity();
        var pointId = UUID.randomUUID().toString();

        var raiseContent = ChannelMessageMeta.encode("##FSI##",
                Map.of("entryType", "RAISE", "role", "momentum-strategy", "round", "1"),
                "AAPL thesis");
        var raiseMsg = new MessageView(1L, UUID.randomUUID(), "rule:momentum@v1",
                MessageType.COMMAND, raiseContent, pointId, null, null,
                "debate", List.of(), null, Instant.now(), null, 0);
        state = projection.apply(state, raiseMsg);

        var agreeContent = ChannelMessageMeta.encode("##FSI##",
                Map.of("entryType", "AGREE", "role", "mean-reversion-strategy", "round", "1"),
                "I concur");
        var agreeMsg = new MessageView(2L, UUID.randomUUID(), "rule:mean-reversion@v1",
                MessageType.RESPONSE, agreeContent, pointId, null, null,
                "debate", List.of(), null, Instant.now(), null, 0);
        state = projection.apply(state, agreeMsg);

        assertEquals("AGREED", state.points().get(pointId).status());
    }
}
