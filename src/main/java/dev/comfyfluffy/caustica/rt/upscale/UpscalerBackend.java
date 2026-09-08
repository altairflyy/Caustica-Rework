package dev.comfyfluffy.caustica.rt.upscale;

import dev.comfyfluffy.caustica.rt.frame.FrameContext;

/**
 * Lifecycle contract for one temporal or native upscale implementation.
 *
 * @param <I> immutable dispatch request specific to the implementation
 */
public interface UpscalerBackend<I> {
    /** Whether this backend can currently occupy the single upscale slot. */
    boolean available();

    /** Render extent recommended for a selected display extent. */
    FrameContext.Extent recommendedRenderExtent(int displayWidth, int displayHeight);

    /** Request that temporal history be discarded before the next dispatch. */
    void requestReset();

    /** Execute or record one upscale operation. */
    UpscaleResult execute(I input);

    /** Release backend-owned resources. Implementations must be idempotent. */
    void destroy();
}
