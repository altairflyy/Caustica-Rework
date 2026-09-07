package dev.comfyfluffy.caustica.rt.lod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class DhLodMeshSourceTest {
    @Test
    void exposesCapturedDhBoundaryWithoutRequiringDhAtStartup() {
        DhLodMeshSource source = new DhLodMeshSource();

        assertNotNull(source.snapshot());
        assertDoesNotThrow(source::revision);
        assertDoesNotThrow(source::renderDistanceChunks);
        assertDoesNotThrow(source::reset);
    }
}
