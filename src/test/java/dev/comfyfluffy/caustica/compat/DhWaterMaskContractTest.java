package dev.comfyfluffy.caustica.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DhWaterMaskContractTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void authoritativeDhMaterialReachesTheVisibleFragment() throws IOException {
        String vertex = read("src/main/resources/assets/distanthorizons/shaders/terrain/caustica_water_mask/vert.vsh");
        String fragment = read("src/main/resources/assets/distanthorizons/shaders/terrain/caustica_water_mask/frag.fsh");
        assertTrue(vertex.contains("in int irisMaterial"));
        assertTrue(vertex.contains("vMaterialId = uint(irisMaterial)"));
        assertTrue(vertex.contains("vPackedLight = meta & 0xFFu"));
        assertTrue(vertex.contains("vBaseColor"));
        assertTrue(fragment.contains("vMaterialId == 12u"));
        assertTrue(fragment.contains("layout(location = 1) out vec4 surfaceData"));
        assertTrue(fragment.contains("float(packedSurface) / 255.0"));
        assertFalse(fragment.contains("nativeBackground"));
        assertFalse(fragment.contains("biome"));
    }

    @Test
    void surfaceDataIsACompactSamePassAttachmentWithoutReadback() throws IOException {
        String owner = read("src/main/java/dev/comfyfluffy/caustica/compat/DistantHorizonsWaterMask.java");
        assertTrue(owner.contains("GpuFormat.RGBA8_UNORM"));
        assertTrue(owner.contains("ColorTargetState.WRITE_ALL"));
        assertTrue(owner.contains("withColorAttachment(mask"));
        assertTrue(owner.contains("USAGE_RENDER_ATTACHMENT"));
        assertTrue(owner.contains("USAGE_TEXTURE_BINDING"));
        assertFalse(owner.contains("copyTextureToBuffer"));
        assertFalse(owner.contains("waitIdle"));
    }

    @Test
    void onlyNativeDhTerrainPipelineAndPassAreExtended() throws IOException {
        String terrainMixin = read("src/main/java/dev/comfyfluffy/caustica/mixin/DistantHorizonsTerrainWaterMaskMixin.java");
        String passMixin = read("src/main/java/dev/comfyfluffy/caustica/mixin/DistantHorizonsRenderPassWaterMaskMixin.java");
        assertTrue(terrainMixin.contains("BlazeDhTerrainRenderer"));
        assertTrue(terrainMixin.contains("terrain/caustica_water_mask/vert"));
        assertTrue(terrainMixin.contains("terrain/caustica_water_mask/frag"));
        assertTrue(passMixin.contains("RenderPassWrapper"));
        assertFalse(terrainMixin.contains("renderLods"));
        assertFalse(passMixin.contains("draw"));
    }

    @Test
    void packedSurfaceAttachmentFeedsTerrainAndWaterCompositing() throws IOException {
        String shader = read("shaders/display/display.comp");
        String reflection = read("shaders/world/dh_reflection.rgen.slang");
        String reflectionPipeline = read(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtDhReflectionPipeline.java");
        assertTrue(shader.contains("uniform sampler2D nativeSurfaceData"));
        assertTrue(shader.contains("surfaceKey(surface)"));
        assertTrue(shader.contains("dhWater(key)"));
        assertTrue(shader.contains("shadeDhTerrain"));
        assertTrue(shader.contains("shadeDhWater"));
        assertTrue(reflection.contains("(surfaceKey & 8u) == 0u"));
        assertTrue(reflectionPipeline.contains("VK_FILTER_NEAREST"));
        assertFalse(shader.contains("Temporary inspection"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative));
    }
}
