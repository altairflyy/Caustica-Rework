package dev.comfyfluffy.caustica.rt.reconstruction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgfResourcesTest {
    @Test
    void resetRestoresTheReferenceParityAndHistoryState() {
        SvgfResources resources = new SvgfResources();
        resources.writeToPing = false;
        resources.hasHistory = true;
        resources.resetHistory();
        assertTrue(resources.writeToPing);
        assertFalse(resources.hasHistory);
    }
}
