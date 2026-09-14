package dev.comfyfluffy.caustica.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class DhFaceRecipeResolverTest {
    private static final float[] S = {0, 1, 1, 0};
    private static final float[] T = {0, 0, 1, 1};

    @Test
    void fitsIdentityAndRotatedFaceUvExactly() {
        assertArrayEquals(new float[]{1, 0, 0, 0, 1, 0},
                DhFaceRecipeResolver.fitAffineForTest(S, T,
                        new float[]{0, 1, 1, 0}, new float[]{0, 0, 1, 1}), 1.0e-6f);
        assertArrayEquals(new float[]{0, 1, 0, -1, 0, 1},
                DhFaceRecipeResolver.fitAffineForTest(S, T,
                        new float[]{0, 0, 1, 1}, new float[]{1, 0, 0, 1}), 1.0e-6f);
    }

    @Test
    void rejectsUvThatCannotBeRepresentedByOneAffineLayer() {
        assertNull(DhFaceRecipeResolver.fitAffineForTest(S, T,
                new float[]{0, 1, 0.2f, 0}, new float[]{0, 0, 1, 1}));
    }

    @Test
    void dirtStoneAndSandShapeResolveAsSingleLayerRecipes() {
        for (int materialId : new int[]{11, 12, 13}) {
            var recipe = DhFaceRecipeResolver.assembleForTest(layer(materialId, 1.0f, 1.0f, 1.0f,
                    false, true, false));
            assertNotNull(recipe);
            assertEquals(materialId, recipe.base().materialId());
            assertNull(recipe.overlay());
        }
    }

    @Test
    void grassTopIsSingleTintedAndGrassSideRemainsMultilayerWithoutDoubleTint() {
        var top = DhFaceRecipeResolver.assembleForTest(layer(20, 0.31f, 0.72f, 0.19f,
                true, true, false));
        assertNotNull(top);
        assertNull(top.overlay());
        assertEquals(0.31f, top.base().tintR());
        assertEquals(0.72f, top.base().tintG());
        assertEquals(0.19f, top.base().tintB());

        var side = DhFaceRecipeResolver.assembleForTest(
                layer(21, 1.0f, 1.0f, 1.0f, false, true, false),
                layer(22, 0.31f, 0.72f, 0.19f, true, false, false));
        assertNotNull(side);
        assertEquals(1.0f, side.base().tintR());
        assertEquals(0.31f, side.overlay().tintR());
    }

    @Test
    void ambiguousPartialAndCutoutOnlyFacesRemainFallback() {
        assertNull(DhFaceRecipeResolver.assembleForTest(
                layer(1, 1, 1, 1, false, true, false),
                layer(2, 1, 1, 1, true, false, false),
                layer(3, 1, 1, 1, true, false, false)));
        assertNull(DhFaceRecipeResolver.assembleForTest(layer(1, 1, 1, 1,
                false, true, true)));
        assertNull(DhFaceRecipeResolver.assembleForTest(layer(1, 1, 1, 1,
                true, false, false)));
    }

    @Test
    void taggedLeafCutoutUsesSolidCoverageFillWhileGenericCutoutStillFallsBack() {
        var fill = new DhMaterialProvenance.CutoutFill(0.08f, 0.31f, 0.05f);
        var leaves = DhFaceRecipeResolver.assembleForTest(new DhFaceRecipeResolver.LayerCandidate(
                30, 0.4f, 0.8f, 0.3f, 1, 0, 0, 0, 1, 0,
                true, false, fill, false));
        assertNotNull(leaves);
        assertNull(leaves.overlay());
        assertEquals(fill, leaves.cutoutFill());

        var genericCutout = DhFaceRecipeResolver.assembleForTest(layer(
                31, 1, 1, 1, true, false, false));
        assertNull(genericCutout);
    }

    @Test
    void vanillaDirtStoneSandAndGrassAssetsHaveTheExpectedRecipeShapes() throws IOException {
        assertEquals("minecraft:block/dirt", model("dirt").getAsJsonObject("textures").get("all").getAsString());
        assertEquals("minecraft:block/stone", model("stone").getAsJsonObject("textures").get("all").getAsString());
        assertEquals("minecraft:block/sand", model("sand").getAsJsonObject("textures").get("all").getAsString());

        JsonObject grass = model("grass_block");
        var elements = grass.getAsJsonArray("elements");
        assertEquals(2, elements.size());
        JsonObject baseFaces = elements.get(0).getAsJsonObject().getAsJsonObject("faces");
        JsonObject overlayFaces = elements.get(1).getAsJsonObject().getAsJsonObject("faces");
        assertEquals(0, baseFaces.getAsJsonObject("up").get("tintindex").getAsInt());
        assertNull(baseFaces.getAsJsonObject("north").get("tintindex"));
        assertEquals(0, overlayFaces.getAsJsonObject("north").get("tintindex").getAsInt());
    }

    private static JsonObject model(String name) throws IOException {
        String path = "assets/minecraft/models/block/" + name + ".json";
        var stream = DhFaceRecipeResolverTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static DhFaceRecipeResolver.LayerCandidate layer(int materialId, float r, float g, float b,
                                                              boolean overlay, boolean solid, boolean invalid) {
        return new DhFaceRecipeResolver.LayerCandidate(materialId, r, g, b,
                1, 0, 0, 0, 1, 0, overlay, solid, null, invalid);
    }
}
