package dev.comfyfluffy.caustica.rt.lighting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharcRadianceCacheTest {
    @Test
    void startsWithoutAWorldChange() {
        SharcRadianceCache cache = new SharcRadianceCache();
        assertFalse(cache.sceneChanged(null, 0));
    }

    @Test
    void facadeMaterializesTraceBindingsAndOwnsLifecyclePolicy() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/lighting/SharcRadianceCache.java"));

        assertTrue(source.contains("return new Bindings(cacheAddress(), params(), params2(), params3(), gridOrigin(terrain));"));
        assertTrue(source.contains("public void sync(RtContext ctx, ClientLevel world, int dimension, long frameIndex)"));
    }
}
