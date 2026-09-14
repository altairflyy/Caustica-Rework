package dev.comfyfluffy.caustica.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DhFaceRecipeContractTest {
    @Test
    void cpuAndBothHitShadersShareTheExtendedDhSectionStride() throws IOException {
        String terrain = read("src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtLodTerrain.java");
        String common = read("shaders/world/world_common.slang");
        String closest = read("shaders/world/world.rchit.slang");
        String anyHit = read("shaders/world/world.rahit.slang");

        assertTrue(terrain.contains("Math.multiplyExact(40L, count)"));
        assertTrue(terrain.contains("section * 40L"));
        assertTrue(common.contains("public struct DhSection"));
        assertTrue(common.contains("public uint64_t faceRecipeAddr"));
        assertTrue(closest.contains("ConstPtr<DhSection>(pc.dhTableAddr)"));
        assertTrue(anyHit.contains("ConstPtr<DhSection>(pc.dhTableAddr)"));
    }

    @Test
    void recipeUsesNearLayersAndKeepsProductiveScalarFallback() throws IOException {
        String terrain = read("src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtLodTerrain.java");
        String shader = read("shaders/world/world.rchit.slang");

        assertTrue(terrain.contains("DhMaterialProvenance.faceRecipe(provenanceId, normalIndex)"));
        assertTrue(terrain.contains("if (faceRecipe == null)"));
        assertTrue(terrain.contains("DhMaterialProvenance.gain(provenanceId, normalIndex, textureTileId)"));
        assertTrue(shader.contains("out bool filledCutout"));
        assertTrue(shader.contains("== 0xFFFFFFFFu) return base.rgb"));
        assertTrue(shader.contains("secondKind == 0xFFFFFFFEu"));
        assertTrue(shader.contains("filledCutout = base.a < TERRAIN_ALPHA_CUTOFF"));
        assertTrue(shader.contains("return filledCutout ? coveredMean : base.rgb"));
        assertTrue(shader.contains("return lerp(base.rgb, overlay.rgb, overlay.a)"));
        assertTrue(shader.contains("dhNearLayerGradientUv(DhFaceLayer layer, MaterialHeader header"));
        assertTrue(shader.contains("Keep the projected UV unwrapped here"));
        assertTrue(shader.contains("dhNearLayerLod(DhSection sec, uint primitiveId, DhFaceLayer layer"));
        assertTrue(shader.contains("HitTriangleVertexPosition(0), HitTriangleVertexPosition(1), HitTriangleVertexPosition(2)"));
        assertTrue(shader.contains("dhNearLayerLod(sec, primitiveId, layer, header, rayCone)"));
        assertTrue(shader.contains("? dhNearFaceAlbedo"));
        assertTrue(shader.contains(": dhAtlasAlbedo"));
    }

    @Test
    void foliageDebugMarksOnlyFilledTexelsRedAndFallbackLeavesYellow() throws IOException {
        String shader = read("shaders/world/world.rchit.slang");
        String guides = read("shaders/world/guides.slang");
        String options = read("src/main/java/dev/comfyfluffy/caustica/client/RtVideoOptions.java");

        assertTrue(shader.contains("pc.debugView == 16u && dhMaterial == DH_MATERIAL_LEAVES"));
        assertTrue(shader.contains("if (!dhHasRecipe)"));
        assertTrue(shader.contains("dpr.aux1 == 1u ? float3(1.0, 0.0, 1.0)"));
        assertTrue(shader.contains(": float3(1.0, 1.0, 0.0)"));
        assertTrue(shader.contains("else if (dhFilledCutout)"));
        assertTrue(shader.contains("dhAlbedo = float3(1.0, 0.0, 0.0)"));
        assertTrue(guides.contains("pc.debugView == 2u || pc.debugView == 16u"));
        assertTrue(options.contains("13, 14, 15, 16, 17, 18, 19, 20"));
        assertTrue(options.contains("Math.clamp(setting.value(), 0, 20)"));
    }

    @Test
    void foliageDiagnosticsSeparateTextureTintAndPreAoAlbedo() throws IOException {
        String shader = read("shaders/world/world.rchit.slang");
        String guides = read("shaders/world/guides.slang");

        assertTrue(shader.contains("pc.debugView == 17u"));
        assertTrue(shader.contains("dhAlbedo = dhHasRecipe\n                    ? dhBaseRawRgb"));
        assertTrue(shader.contains("pc.debugView == 18u"));
        assertTrue(shader.contains("? dhBaseTint"));
        assertTrue(shader.contains("pc.debugView == 19u"));
        assertTrue(shader.contains("terrainPreAoAlbedo"));
        assertTrue(guides.contains("pc.debugView == 17u || pc.debugView == 18u || pc.debugView == 19u"));
    }

    @Test
    void foliageMaterialIdentityUsesTheSameHashForNearAndFar() throws IOException {
        String shader = read("shaders/world/world.rchit.slang");
        String guides = read("shaders/world/guides.slang");

        assertTrue(shader.contains("materialIdDebugColor(uint materialId)"));
        assertTrue(shader.contains("materialIdDebugColor(asuint(debugRecipe.layers[0].materialTint.x))"));
        assertTrue(shader.contains("materialIdDebugColor(pr.materialId)"));
        assertTrue(guides.contains("pc.debugView == 20u"));
    }

    @Test
    void lodLeavesUseNearEquivalentAlphaCoverageOutsideTheDiagnosticView() throws IOException {
        String anyHit = read("shaders/world/world.rahit.slang");
        String pipeline = read("src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtPipeline.java");

        assertTrue(anyHit.contains("dhMaterial == DH_MATERIAL_LEAVES && pc.debugView != 16u"));
        assertTrue(anyHit.contains("hasRecipe ? dhRecipeLeafAlpha"));
        assertTrue(anyHit.contains(": dhFallbackLeafAlpha"));
        assertTrue(anyHit.contains("alpha < TERRAIN_ALPHA_CUTOFF"));
        assertTrue(anyHit.contains("[[vk::binding(13, 0)]] Sampler2D dhBlockAtlas"));
        assertTrue(pipeline.contains("VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR\n"
                + "                                | (hasAhit ? VK_SHADER_STAGE_ANY_HIT_BIT_KHR : 0)"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
