package dev.comfyfluffy.caustica.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DhNativeHookHybridContractTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void levelRendererIsObservedButNeverCancelled() throws IOException {
        String source = read("src/main/java/dev/comfyfluffy/caustica/mixin/LevelRendererMixin.java");
        assertTrue(source.contains("markLevelRendererObserved()"));
        assertFalse(source.contains("cancellable = true"));
        assertFalse(source.contains("ci.cancel()"));
    }

    @Test
    void vanillaChunkGroupsAreSuppressedAfterDhOrder800Hook() throws IOException {
        String source = read("src/main/java/dev/comfyfluffy/caustica/mixin/ChunkSectionsToRenderMixin.java");
        assertTrue(source.contains("method = \"renderGroup\""));
        assertTrue(source.contains("order = 1000"));
        assertTrue(source.contains("shouldSuppressVanillaTerrain()"));
        assertTrue(source.contains("ci.cancel()"));
    }

    @Test
    void nativeHookStatComesFromTheRealDhRenderEntryPoint() throws IOException {
        String observer = read("src/main/java/dev/comfyfluffy/caustica/mixin/DistantHorizonsActiveRenderMixin.java");
        String controller = read("src/main/java/dev/comfyfluffy/caustica/client/VanillaRenderController.java");
        assertTrue(observer.contains("markNativeDhHookObserved()"));
        assertTrue(observer.contains("publishActiveRasterContainers"));
        assertTrue(controller.contains("count(\"nativeDhHookExecutions\", 1)"));
        int suppression = controller.indexOf("public void markTerrainGroupSuppressed");
        int observation = controller.indexOf("public void markNativeDhHookObserved", suppression);
        assertFalse(controller.substring(suppression, observation).contains("nativeDhHookExecutions"));
    }

    @Test
    void dhSettingsRemainAuthoritativeAndLivePolled() throws IOException {
        String compat = read("src/main/java/dev/comfyfluffy/caustica/compat/DistantHorizonsCompat.java");
        String lod = read("src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtLodTerrain.java");
        String config = read("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String options = read("src/main/java/dev/comfyfluffy/caustica/client/RtVideoOptions.java");
        String screen = read("src/main/java/dev/comfyfluffy/caustica/client/gui/RtVideoOptionsScreen.java");
        assertTrue(compat.contains("chunkRenderDistance"));
        assertTrue(compat.contains("maxHorizontalResolution"));
        assertTrue(compat.contains("horizontalQuality"));
        assertTrue(compat.contains("lodShading"));
        assertTrue(lod.contains("DistantHorizonsCompat.lodQuality()"));
        assertTrue(lod.contains("DistantHorizonsCompat.renderDistanceChunks()"));
        assertTrue(options.contains("reloadRenderDataCache()"));
        assertTrue(options.contains("DistantHorizonsCompat.dhRtRingEnabled()"));
        assertTrue(screen.contains("if (DistantHorizonsCompat.dhRtRingEnabled())"));
        assertFalse(compat.substring(compat.indexOf("public static boolean reloadRenderDataCache()"),
                compat.indexOf("public static LodQuality lodQuality()"))
                .contains("VoxyCompat.reset()"));
        assertFalse(config.contains("terrain.dh-rt-enabled"));
        assertFalse(config.contains("terrain.dh-rt-distance-chunks"));
        assertFalse(config.contains("terrain.dh-far-lighting"));
        assertFalse(config.contains("terrain.dh-water-mask-debug"));
    }

    @Test
    void canonicalFarUsesOneRtAndDisplayAuthority() throws IOException {
        String shader = read("shaders/display/display.comp");
        String compat = read("src/main/java/dev/comfyfluffy/caustica/compat/DistantHorizonsCompat.java");
        String lod = read("src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtLodTerrain.java");
        assertTrue(shader.contains("tonemap(rt.rgb, exposure)"));
        assertFalse(shader.contains("nativeBackground"));
        assertFalse(shader.contains("nativeWaterMask"));
        assertFalse(shader.contains("dhReflection"));
        assertTrue(compat.contains("return ACTIVE_FAR_MESHES"));
        assertTrue(lod.contains("MAX_ACTIVE_BLAS = 64"));
        assertTrue(lod.contains("DH is the sole authority for FAR distance"));
        assertFalse(Files.exists(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/proxy/DhFarFieldProxy.java")));
        assertFalse(Files.exists(ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDhReflectionPipeline.java")));
    }

    @Test
    void dhDiagnosticsHaveLiveProducers() throws IOException {
        String stats = read("src/main/java/dev/comfyfluffy/caustica/rt/RtFrameStats.java");
        String composite = read("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String overlay = read("src/main/java/dev/comfyfluffy/caustica/mixin/DebugScreenOverlayMixin.java");
        assertTrue(stats.contains("dhActiveRasterContainers"));
        assertTrue(stats.contains("dhUnmatchedRasterContainers"));
        assertTrue(composite.contains("count(\"dhActiveRasterContainers\""));
        assertTrue(composite.contains("count(\"dhUnmatchedRasterContainers\""));
        assertTrue(overlay.contains("wasNativeDhHookObservedThisFrame()"));
        assertTrue(overlay.contains("DistantHorizonsCompat.dhRenderDistanceChunks()"));
        assertTrue(overlay.contains("DistantHorizonsCompat.dhLodQuality()"));
        assertFalse(stats.contains("manualDhRenderCalls"));
    }

    @Test
    void rtDisableRestoresNormalChunkGroupBodies() throws IOException {
        String controller = read("src/main/java/dev/comfyfluffy/caustica/client/VanillaRenderController.java");
        int method = controller.indexOf("public boolean shouldSuppressVanillaTerrain()");
        int next = controller.indexOf("public void markTerrainGroupSuppressed", method);
        String gate = controller.substring(method, next);
        assertTrue(gate.contains("if (!this.rtActive)"));
        assertTrue(gate.contains("return false;"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative));
    }
}
