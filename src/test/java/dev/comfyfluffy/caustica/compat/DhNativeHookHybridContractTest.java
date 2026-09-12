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
        assertTrue(source.contains("ChunkSectionLayerGroup.OPAQUE"));
        assertTrue(source.contains("ChunkSectionLayerGroup.TRANSLUCENT"));
        assertTrue(source.contains("shouldSuppressVanillaTerrain()"));
        assertTrue(source.contains("ci.cancel()"));
        assertFalse(source.contains("LevelRenderer" + ".render"));
    }

    @Test
    void manualDhRenderPathIsAbsentAndF6RemainsObservationOnly() throws IOException {
        String compat = read("src/main/java/dev/comfyfluffy/caustica/compat/DistantHorizonsCompat.java");
        String seam = read("src/main/java/dev/comfyfluffy/caustica/client/WorldRenderScaler.java");
        assertFalse(compat.contains("getMethod(\"renderLods\")"));
        assertFalse(compat.contains("void renderNativeRaster"));
        assertFalse(seam.contains("renderNativeRaster"));
        assertTrue(compat.contains("nativeRasterActive()"));
        assertTrue(compat.contains("rendererMode"));
        assertFalse(compat.contains("rendererMode.set"));
    }

    @Test
    void dhRtRingAndNativeRasterAreIndependent() throws IOException {
        String compat = read("src/main/java/dev/comfyfluffy/caustica/compat/DistantHorizonsCompat.java");
        assertTrue(compat.contains("if (!dhRtRingEnabled())"));
        assertTrue(compat.contains("public static boolean nativeHookInstalled()"));
        assertTrue(compat.contains("return LOADED;"));
    }

    @Test
    void hybridCoverageUsesDhDepthNotColorAlphaOrTwoByTwoHeuristic() throws IOException {
        String shader = read("shaders/display/display.comp");
        assertTrue(shader.contains("nativeBackgroundDepth"));
        assertTrue(shader.contains("nativeDepthClear"));
        assertTrue(shader.contains("abs(nativeDepth - pc.nativeDepthClear)"));
        assertFalse(shader.contains("native.a"));
        assertFalse(shader.contains("for (int y = 0; y <= 1"));
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

    @Test
    void farLightingUsesSamePassSurfaceDataAndOneDisplayTransform() throws IOException {
        String shader = read("shaders/display/display.comp");
        String scaler = read("src/main/java/dev/comfyfluffy/caustica/client/WorldRenderScaler.java");
        String config = read("src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java");
        String pipeline = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDisplayPipeline.java");
        assertTrue(shader.contains("nativeInvViewProj"));
        assertTrue(shader.contains("reconstructDhPosition"));
        assertTrue(shader.contains("nativeSurfaceData"));
        assertTrue(shader.contains("dhFaceNormal"));
        assertTrue(shader.contains("dhSkyLight"));
        assertTrue(shader.contains("dhBlockLight"));
        assertTrue(shader.contains("? mix(nativeRadiance, rt.rgb, rtCoverage"));
        assertTrue(shader.contains(": nativeRadiance;"));
        assertTrue(shader.contains("tonemap(sceneRadiance, exposure)"));
        assertTrue(shader.contains("tonemapHdr(sceneRadiance, exposure)"));
        assertFalse(shader.contains("Temporary inspection"));
        assertFalse(shader.contains("rayQueryEXT"));
        assertFalse(shader.contains("traceRayEXT"));
        assertTrue(pipeline.contains("PUSH_BYTES = 32 * Integer.BYTES"));
        assertFalse(pipeline.contains("push.putInt(128"));
        assertTrue(scaler.contains("DistantHorizonsCompat.inverseViewProjection"));
        assertTrue(config.contains("terrain.dh-far-lighting"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative));
    }
}
