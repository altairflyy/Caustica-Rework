package dev.comfyfluffy.caustica.rt.lod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class VoxyBridgeLodMeshSourceTest {
    @Test
    void bridgeSourceFallsBackCleanlyWhenOptionalBridgeIsUnavailable() {
        VoxyBridgeLodMeshSource source = new VoxyBridgeLodMeshSource();

        assertNotNull(source.snapshot());
        assertDoesNotThrow(source::available);
        assertDoesNotThrow(source::active);
        assertDoesNotThrow(source::revision);
        assertDoesNotThrow(source::renderDistanceChunks);
        assertDoesNotThrow(source::reset);
    }
}
