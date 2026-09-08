package dev.comfyfluffy.caustica.rt.scene;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import dev.comfyfluffy.caustica.rt.entity.RtEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Assembles the immutable frame scene and the exact ordered TLAS instance segments. */
public final class SceneAssembler {
    public static final SceneAssembler INSTANCE = new SceneAssembler();

    private SceneAssembler() {
    }

    public List<RtAccel.Instance> staticInstances(TerrainSceneContribution terrain,
                                                   LodSceneContribution lod) {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(lod, "lod");
        ArrayList<RtAccel.Instance> result = new ArrayList<>(
                terrain.instances().size() + lod.instances().size());
        result.addAll(terrain.instances());
        result.addAll(lod.instances());
        return List.copyOf(result);
    }

    public RtScene assemble(TerrainSceneContribution terrain,
                            LodSceneContribution lod,
                            RtEntities.EntitySceneContribution entities,
                            RtScene.LightView lightView,
                            RtScene.MaterialView materialView,
                            long sceneGeneration) {
        Objects.requireNonNull(entities, "entities");
        List<RtAccel.Instance> expectedBase = staticInstances(terrain, lod);
        if (!expectedBase.equals(entities.baseInstances())) {
            throw new IllegalArgumentException("entity base instances do not match terrain + LOD contribution");
        }
        return new RtScene(terrain.instances(), entities.dynamicInstances(), lod.instances(),
                lightView, materialView, sceneGeneration);
    }

    public TlasInput tlasInput(RtScene scene) {
        Objects.requireNonNull(scene, "scene");
        ArrayList<RtAccel.Instance> base = new ArrayList<>(
                scene.fullTerrainInstances().size() + scene.lodInstances().size());
        base.addAll(scene.fullTerrainInstances());
        base.addAll(scene.lodInstances());
        return new TlasInput(base, scene.entityInstances());
    }

    public record TlasInput(List<RtAccel.Instance> baseInstances,
                            List<RtAccel.Instance> dynamicInstances) {
        public TlasInput {
            baseInstances = List.copyOf(Objects.requireNonNull(baseInstances, "baseInstances"));
            dynamicInstances = List.copyOf(Objects.requireNonNull(dynamicInstances, "dynamicInstances"));
        }
    }
}
