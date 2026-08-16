package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradeProvenanceTest {

    @Test
    void allFieldsPopulated() {
        var channelId = UUID.randomUUID();
        var commitmentId = UUID.randomUUID();
        var provenance = new TradeProvenance(channelId, commitmentId, "CONSENSUS", 0.85);

        assertEquals(channelId, provenance.deliberationChannelId());
        assertEquals(commitmentId, provenance.commitmentId());
        assertEquals("CONSENSUS", provenance.convergenceState());
        assertEquals(0.85, provenance.confidence());
    }

    @Test
    void nullChannelIdThrows() {
        assertThrows(NullPointerException.class,
                () -> new TradeProvenance(null, UUID.randomUUID(), "CONSENSUS", 0.85));
    }

    @Test
    void nullCommitmentIdThrows() {
        assertThrows(NullPointerException.class,
                () -> new TradeProvenance(UUID.randomUUID(), null, "CONSENSUS", 0.85));
    }

    @Test
    void nullConvergenceStateThrows() {
        assertThrows(NullPointerException.class,
                () -> new TradeProvenance(UUID.randomUUID(), UUID.randomUUID(), null, 0.85));
    }
}
