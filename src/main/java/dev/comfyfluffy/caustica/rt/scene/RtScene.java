package dev.comfyfluffy.caustica.rt.scene;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;

import java.util.List;
import java.util.Objects;

/**
 * Minimal immutable frame-scene contract produced by the scene assembler and consumed by TLAS assembly.
 *
 * <p>This contract deliberately models only the instance segments and published views already
 * required by the current TLAS/frame path. It owns no GPU resources and is not a scene database.
 * {@code sceneGeneration} is an independent scene-domain token; callers must not substitute light
 * generation or material epoch when no canonical scene generation is available.</p>
 */
public record RtScene(
        List<RtAccel.Instance> fullTerrainInstances,
        List<RtAccel.Instance> entityInstances,
        List<RtAccel.Instance> lodInstances,
        LightView lightView,
        MaterialView materialView,
        long sceneGeneration
) {
    /** Explicit unversioned value while the legacy renderer has no canonical scene-generation authority. */
    public static final long LEGACY_SCENE_GENERATION = 0L;

    public RtScene {
        fullTerrainInstances = List.copyOf(Objects.requireNonNull(
                fullTerrainInstances, "fullTerrainInstances"));
        entityInstances = List.copyOf(Objects.requireNonNull(entityInstances, "entityInstances"));
        lodInstances = List.copyOf(Objects.requireNonNull(lodInstances, "lodInstances"));
        lightView = Objects.requireNonNull(lightView, "lightView");
        materialView = Objects.requireNonNull(materialView, "materialView");
        if (sceneGeneration < 0L) {
            throw new IllegalArgumentException("sceneGeneration must be non-negative");
        }
    }

    /** Published light resources used by the current frame. */
    public record LightView(
            long lightAddress,
            long globalAliasAddress,
            long localAliasAddress,
            long gridCellAddress,
            long gridSpanAddress,
            int lightCount,
            int lightGeneration
    ) {}

    /** Published material table used by the current frame. */
    public record MaterialView(long tableAddress, long materialEpoch) {}
}
