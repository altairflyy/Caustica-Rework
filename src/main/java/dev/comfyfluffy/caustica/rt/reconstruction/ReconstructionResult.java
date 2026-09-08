package dev.comfyfluffy.caustica.rt.reconstruction;

import dev.comfyfluffy.caustica.rt.accel.RtImage;

import java.util.Objects;

/**
 * Common output handed from reconstruction to the existing upscale stage.
 *
 * @param output image selected for downstream upscale/display processing
 * @param executed whether the selected backend completed reconstruction
 */
public record ReconstructionResult(RtImage output, boolean executed) {
    public ReconstructionResult {
        Objects.requireNonNull(output, "output");
    }
}
