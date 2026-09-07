package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtLodTerrainTest {
    @Test
    void recognizesDistantHorizonsEmissiveMaterialIndices() {
        assertTrue(RtLodTerrain.isDhEmissiveMaterial(6), "lava must emit");
        assertTrue(RtLodTerrain.isDhEmissiveMaterial(15), "illuminated material must emit");
        assertFalse(RtLodTerrain.isDhEmissiveMaterial(12), "water is handled separately");
        assertFalse(RtLodTerrain.isDhEmissiveMaterial(0), "unknown remains ordinary terrain");
    }

    @Test
    void splitsBakedGlassCoverageAcrossEntryAndExitInterfaces() {
        for (float coverage : new float[]{0.06f, 0.24f, 0.50f, 0.72f}) {
            float interfaceAlpha = RtLodTerrain.dhGlassInterfaceAlpha(coverage);
            float recomposed = 1.0f - (1.0f - interfaceAlpha) * (1.0f - interfaceAlpha);
            assertEquals(coverage, recomposed, 1.0e-6f);
        }
        assertEquals(RtLodTerrain.dhGlassInterfaceAlpha(0.24f),
                RtLodTerrain.dhGlassInterfaceAlpha(1.0f), 1.0e-6f,
                "opaque alpha in DH's transparent VBO must use the clear-glass fallback");
    }
}
