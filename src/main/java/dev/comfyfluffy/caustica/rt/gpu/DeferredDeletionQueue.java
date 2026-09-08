package dev.comfyfluffy.caustica.rt.gpu;

import dev.comfyfluffy.caustica.rt.RtGpuExecutor;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Published-resource retirement facade over the existing graphics timeline.
 *
 * <p>The executor remains the owner of timeline values, queue storage, wake-up,
 * completion polling, and callback execution. This facade only centralizes the
 * runtime retirement entry point without changing token or destruction timing.</p>
 */
public final class DeferredDeletionQueue {
    private final BiConsumer<RtGpuExecutor.GraphicsUse, Runnable> graphicsRetirement;
    private final BiConsumer<RtGpuExecutor.TrackedGraphicsUse, Runnable> trackedRetirement;

    public DeferredDeletionQueue(RtGpuExecutor executor) {
        this(Objects.requireNonNull(executor, "executor")::retireAfterGraphics,
                executor::retireAfterGraphics);
    }

    DeferredDeletionQueue(
            BiConsumer<RtGpuExecutor.GraphicsUse, Runnable> graphicsRetirement,
            BiConsumer<RtGpuExecutor.TrackedGraphicsUse, Runnable> trackedRetirement) {
        this.graphicsRetirement = Objects.requireNonNull(graphicsRetirement, "graphicsRetirement");
        this.trackedRetirement = Objects.requireNonNull(trackedRetirement, "trackedRetirement");
    }

    public void retireAfterGraphics(RtGpuExecutor.GraphicsUse lastUse, Runnable destroy) {
        graphicsRetirement.accept(lastUse, destroy);
    }

    public void retireAfterGraphics(RtGpuExecutor.TrackedGraphicsUse trackedUse, Runnable destroy) {
        trackedRetirement.accept(trackedUse, destroy);
    }
}
