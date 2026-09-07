package dev.comfyfluffy.caustica.rt.reconstruction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgfResourcesTest {
    @Test
    void resetRestoresTheReferenceParityAndHistoryState() {
        SvgfResources resources = new SvgfResources();
        resources.resetHistory();
        assertTrue(resources.writeToPing());
        assertFalse(resources.hasHistory());
        resources.flipHistory();
        resources.markHistoryValid();
        assertFalse(resources.writeToPing());
        assertTrue(resources.hasHistory());
        resources.resetHistory();
        assertTrue(resources.writeToPing());
        assertFalse(resources.hasHistory());
    }

    @Test
    void previousCameraSnapshotBelongsToTheResourceOwner() {
        SvgfResources resources = new SvgfResources();
        resources.snapshotPreviousCamera(12.5, -3.0, 44.25);

        assertEquals(12.5, resources.previousCameraX());
        assertEquals(-3.0, resources.previousCameraY());
        assertEquals(44.25, resources.previousCameraZ());
    }
}
