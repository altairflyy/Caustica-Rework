package dev.comfyfluffy.caustica.rt.upscale;

import dev.comfyfluffy.caustica.rt.accel.RtImage;

import java.util.Objects;

/** Result supplied to display processing by the selected upscale backend. */
public record UpscaleResult(RtImage output, boolean executed) {
    public UpscaleResult {
        Objects.requireNonNull(output, "output");
    }
}
