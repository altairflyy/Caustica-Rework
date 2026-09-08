package dev.comfyfluffy.caustica.rt.scene;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;

import java.util.List;
import java.util.Objects;

/** Immutable frame snapshot of the currently published LOD proxy. */
public record LodSceneContribution(List<RtAccel.Instance> instances, long tableAddress) {
    public LodSceneContribution {
        instances = List.copyOf(Objects.requireNonNull(instances, "instances"));
    }
}
