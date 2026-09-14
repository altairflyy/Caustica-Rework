package dev.comfyfluffy.caustica.rt.terrain;

import dev.comfyfluffy.caustica.compat.DhMaterialProvenance.FaceLayer;
import dev.comfyfluffy.caustica.compat.DhMaterialProvenance.FaceRecipe;
import dev.comfyfluffy.caustica.compat.DhMaterialProvenance.CutoutFill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DhFaceRecipePackingTest {
    @Test
    void identicalSingleAndMultiRecipesDeduplicateAndKeepTheExistingAbi() {
        RtLodTerrain.PackedMeshBuilder packed = new RtLodTerrain.PackedMeshBuilder(0, 0, 0);
        FaceLayer base = layer(7, 1.0f, 1.0f, 1.0f);
        FaceLayer overlay = layer(8, 0.3f, 0.7f, 0.2f);
        FaceRecipe single = new FaceRecipe(base, null);
        FaceRecipe multi = new FaceRecipe(base, overlay);

        assertEquals(1, packed.recipeId(single));
        assertEquals(1, packed.recipeId(new FaceRecipe(base, null)));
        assertEquals(2, packed.recipeId(multi));
        assertEquals(2, packed.recipeId(new FaceRecipe(base, overlay)));
        assertEquals(48, packed.recipeWords.size()); // two fixed 96-byte ABI entries
        assertEquals(-1, Float.floatToRawIntBits(packed.recipeWords.getFloat(12)));
        assertEquals(8, Float.floatToRawIntBits(packed.recipeWords.getFloat(36)));
    }

    @Test
    void leafCutoutFillReusesTheExistingOverlaySlot() {
        RtLodTerrain.PackedMeshBuilder packed = new RtLodTerrain.PackedMeshBuilder(0, 0, 0);
        FaceRecipe leaves = new FaceRecipe(layer(9, 0.4f, 0.8f, 0.3f), null,
                new CutoutFill(0.08f, 0.31f, 0.05f));

        assertEquals(1, packed.recipeId(leaves));
        assertEquals(24, packed.recipeWords.size()); // unchanged 96-byte recipe ABI
        assertEquals(-2, Float.floatToRawIntBits(packed.recipeWords.getFloat(12)));
        assertEquals(0.08f, packed.recipeWords.getFloat(13), 1.0e-6f);
        assertEquals(0.31f, packed.recipeWords.getFloat(14), 1.0e-6f);
        assertEquals(0.05f, packed.recipeWords.getFloat(15), 1.0e-6f);
    }

    private static FaceLayer layer(int materialId, float r, float g, float b) {
        return new FaceLayer(materialId, r, g, b, 1, 0, 0, 0, 1, 0);
    }
}
