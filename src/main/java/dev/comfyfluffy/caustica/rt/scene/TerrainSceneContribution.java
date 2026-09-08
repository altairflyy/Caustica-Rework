package dev.comfyfluffy.caustica.rt.scene;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;

import java.util.List;
import java.util.Objects;

/** Immutable frame snapshot of the full-resolution terrain TLAS instances. */
public record TerrainSceneContribution(List<RtAccel.Instance> instances) {
    public TerrainSceneContribution {
        instances = List.copyOf(Objects.requireNonNull(instances, "instances"));
    }
}
