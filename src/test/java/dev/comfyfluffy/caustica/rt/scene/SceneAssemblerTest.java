package dev.comfyfluffy.caustica.rt.scene;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import dev.comfyfluffy.caustica.rt.entity.RtEntities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneAssemblerTest {
    @Test
    void preservesTerrainLodEntityOrderAndTlasFields() {
        RtAccel.Instance terrainInstance = instance(11L, 2, 0xff, 0);
        RtAccel.Instance lodInstance = instance(12L, 0xC00000, 0x03, 0);
        RtAccel.Instance entityInstance = instance(13L, 7, 0x03, 8);
        TerrainSceneContribution terrain = new TerrainSceneContribution(List.of(terrainInstance));
        LodSceneContribution lod = new LodSceneContribution(List.of(lodInstance), 31L);
        List<RtAccel.Instance> base = SceneAssembler.INSTANCE.staticInstances(terrain, lod);
        RtEntities.EntitySceneContribution entities = contribution(
                base, List.of(entityInstance), 41L);

        RtScene scene = SceneAssembler.INSTANCE.assemble(
                terrain, lod, entities,
                new RtScene.LightView(51L, 52L, 53L, 54L, 55L, 3, 4),
                new RtScene.MaterialView(61L, 5L), RtScene.LEGACY_SCENE_GENERATION);
        SceneAssembler.TlasInput tlasInput = SceneAssembler.INSTANCE.tlasInput(scene);

        assertEquals(List.of(terrainInstance, lodInstance), tlasInput.baseInstances());
        assertEquals(List.of(entityInstance), tlasInput.dynamicInstances());
        assertEquals(List.of(terrainInstance), scene.fullTerrainInstances());
        assertEquals(List.of(lodInstance), scene.lodInstances());
        assertEquals(List.of(entityInstance), scene.entityInstances());
        assertInstanceFields(tlasInput.baseInstances().get(0), 2, 0xff, 0);
        assertInstanceFields(tlasInput.baseInstances().get(1), 0xC00000, 0x03, 0);
        assertInstanceFields(tlasInput.dynamicInstances().get(0), 7, 0x03, 8);
        assertEquals(0L, scene.sceneGeneration());
        assertEquals(4, scene.lightView().lightGeneration());
        assertEquals(5L, scene.materialView().materialEpoch());
    }

    @Test
    void rejectsAnEntityContributionBuiltFromDifferentStaticInstances() {
        RtAccel.Instance terrainInstance = instance(11L, 2, 0xff, 0);
        TerrainSceneContribution terrain = new TerrainSceneContribution(List.of(terrainInstance));
        LodSceneContribution lod = new LodSceneContribution(List.of(), 0L);
        RtEntities.EntitySceneContribution entities = contribution(List.of(), List.of(), 0L);

        assertThrows(IllegalArgumentException.class, () -> SceneAssembler.INSTANCE.assemble(
                terrain, lod, entities,
                new RtScene.LightView(0L, 0L, 0L, 0L, 0L, 0, 0),
                new RtScene.MaterialView(0L, 0L), RtScene.LEGACY_SCENE_GENERATION));
    }

    private static RtEntities.EntitySceneContribution contribution(
            List<RtAccel.Instance> base, List<RtAccel.Instance> dynamic, long tableAddress) {
        return new RtEntities.EntitySceneContribution(base, dynamic, List.of(), tableAddress);
    }

    private static void assertInstanceFields(RtAccel.Instance instance,
                                             int customIndex, int mask, int sbtOffset) {
        assertEquals(customIndex, instance.customIndex());
        assertEquals(mask, instance.mask());
        assertEquals(sbtOffset, instance.sbtRecordOffset());
    }

    private static RtAccel.Instance instance(long address, int customIndex, int mask, int sbtOffset) {
        return new RtAccel.Instance(new float[] {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0
        }, address, customIndex, mask, sbtOffset);
    }
}
