package dev.comfyfluffy.caustica.rt.lod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DhHybridPolicyTest {
    @Test
    void disabledRingProducesNoRtDistance() {
        assertEquals(0, DhHybridPolicy.rtDistanceChunks(false, 32, 256, 512));
        assertEquals(0, DhHybridPolicy.rtDistanceChunks(true, 0, 256, 512));
    }

    @Test
    void configuredRingIsBoundedByProviderAndHardLimit() {
        assertEquals(32, DhHybridPolicy.rtDistanceChunks(true, 32, 256, 512));
        assertEquals(16, DhHybridPolicy.rtDistanceChunks(true, 32, 16, 512));
        assertEquals(512, DhHybridPolicy.rtDistanceChunks(true, 1024, 2048, 512));
    }

    @Test
    void providerDistanceNeverExpandsConfiguredCap() {
        assertEquals(24, DhHybridPolicy.rtDistanceChunks(true, 24, 4096, 512));
    }

    @Test
    void unavailableProviderProducesNoRing() {
        assertEquals(0, DhHybridPolicy.rtDistanceChunks(true, 24, 0, 512));
        assertEquals(0, DhHybridPolicy.rtDistanceChunks(true, 24, 4096, 0));
    }
}
