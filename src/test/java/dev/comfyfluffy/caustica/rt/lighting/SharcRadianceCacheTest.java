package dev.comfyfluffy.caustica.rt.lighting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SharcRadianceCacheTest {
    @Test
    void startsWithoutAWorldChange() {
        SharcRadianceCache cache = new SharcRadianceCache();
        assertFalse(cache.sceneChanged(null, 0));
    }
}
