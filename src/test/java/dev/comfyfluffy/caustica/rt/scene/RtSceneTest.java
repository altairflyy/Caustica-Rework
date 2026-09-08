package dev.comfyfluffy.caustica.rt.scene;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RtSceneTest {
    private static final float[] IDENTITY = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0
    };

    @Test
    void freezesDistinctInstanceSegmentsWithoutReorderingThem() {
        RtAccel.Instance terrain = new RtAccel.Instance(IDENTITY, 10L, 1, 0xff, 0);
        RtAccel.Instance entity = new RtAccel.Instance(IDENTITY, 20L, 2, 0x03, 4);
        RtAccel.Instance lod = new RtAccel.Instance(IDENTITY, 30L, 3, 0x01, 8);
        ArrayList<RtAccel.Instance> terrainInput = new ArrayList<>(List.of(terrain));

        RtScene scene = new RtScene(terrainInput, List.of(entity), List.of(lod),
                new RtScene.LightView(40L, 41L, 42L, 43L, 44L, 5, 6),
                new RtScene.MaterialView(50L, 7L), RtScene.LEGACY_SCENE_GENERATION);
        terrainInput.clear();

        assertEquals(List.of(terrain), scene.fullTerrainInstances());
        assertEquals(List.of(entity), scene.entityInstances());
        assertEquals(List.of(lod), scene.lodInstances());
        assertThrows(UnsupportedOperationException.class,
                () -> scene.entityInstances().add(terrain));
    }

    @Test
    void sceneGenerationIsIndependentFromLightAndMaterialGenerations() {
        RtScene scene = new RtScene(List.of(), List.of(), List.of(),
                new RtScene.LightView(0L, 0L, 0L, 0L, 0L, 0, 23),
                new RtScene.MaterialView(0L, 47L), RtScene.LEGACY_SCENE_GENERATION);

        assertEquals(0L, scene.sceneGeneration());
        assertEquals(23, scene.lightView().lightGeneration());
        assertEquals(47L, scene.materialView().materialEpoch());
    }

    @Test
    void rejectsNegativeSceneGeneration() {
        assertThrows(IllegalArgumentException.class, () -> new RtScene(
                List.of(), List.of(), List.of(),
                new RtScene.LightView(0L, 0L, 0L, 0L, 0L, 0, 0),
                new RtScene.MaterialView(0L, 0L), -1L));
    }
}
