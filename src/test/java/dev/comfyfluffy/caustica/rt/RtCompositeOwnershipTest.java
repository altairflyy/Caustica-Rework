package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AER-090 characterization for ownership already migrated out of RtComposite. */
final class RtCompositeOwnershipTest {
    @Test
    void compositeRetainsOrchestrationWithoutMigratedResourceLifetimes() throws Exception {
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String upscalers = read("src/main/java/dev/comfyfluffy/caustica/rt/upscale/UpscalerRuntime.java");
        String client = read("src/main/java/dev/comfyfluffy/caustica/client/CausticaClient.java");
        String terrain = read("src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtTerrain.java");

        assertFalse(composite.contains("RestirHistory"));
        assertFalse(composite.contains("SvgfResources"));
        assertFalse(composite.contains("new RtAccel.TlasRing"));
        assertFalse(composite.contains("tlasRing.destroy()"));
        assertFalse(composite.contains("private final FsrUpscalerBackend"));
        assertFalse(composite.contains("private final XessUpscalerBackend"));
        assertFalse(composite.contains("private final NativeUpscalerBackend"));
        assertFalse(composite.contains("fsr().destroy()"));
        assertFalse(composite.contains("xess().destroy()"));
        assertFalse(composite.contains("nativeBackend().destroy()"));
        assertFalse(composite.contains("upscalers.fsr().releaseIfDisabled()"));
        assertFalse(composite.contains("upscalers.xess().releaseIfDisabled()"));
        assertFalse(composite.contains("RtLodTerrain.INSTANCE.shutdown"));

        assertTrue(upscalers.contains("private final FsrUpscalerBackend fsr"));
        assertTrue(upscalers.contains("private final XessUpscalerBackend xess"));
        assertTrue(upscalers.contains("private final NativeUpscalerBackend nativeBackend"));
        assertTrue(upscalers.contains("public void destroy()"));
        assertTrue(upscalers.contains("public void releaseInactiveBackends()"));
        int ownerDestroy = client.indexOf("UpscalerRuntime.INSTANCE.destroy()");
        int fsrShutdown = client.indexOf("FsrRuntime.INSTANCE.shutdown()");
        int xessShutdown = client.indexOf("XessRuntime.INSTANCE.shutdown()");
        assertTrue(ownerDestroy >= 0 && fsrShutdown >= 0 && ownerDestroy < fsrShutdown);
        assertTrue(xessShutdown >= 0 && ownerDestroy < xessShutdown);
        assertTrue(terrain.contains("RtLodTerrain.INSTANCE.shutdown(ctx)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
