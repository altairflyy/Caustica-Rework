package dev.comfyfluffy.caustica.rt.graph;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the logical timeline schedule shared by Caustica's asynchronous build lane and frame lane.
 *
 * <p>This class deliberately does not own Vulkan queues, semaphores, command buffers, resources or
 * submission. {@code RtGpuExecutor} remains responsible for applying this schedule at the existing
 * submission boundaries, preserving the reference overlap and queue topology.</p>
 */
public final class QueueDependencyScheduler {
    public enum QueueLane { GRAPHICS, COMPUTE }
    public enum WorkKind { FRAME, TRANSFER_BUILD }
    public enum Timeline { BUILD_COMPLETION, GRAPHICS_COMPLETION }

    /** Immutable topology for a timeline dependency; values are supplied separately without frame allocations. */
    public record DependencyRoute(QueueLane producer, WorkKind producerWork, QueueLane consumer,
                                  Timeline timeline) {
        public DependencyRoute {
            Objects.requireNonNull(producer, "producer");
            Objects.requireNonNull(producerWork, "producerWork");
            Objects.requireNonNull(consumer, "consumer");
            Objects.requireNonNull(timeline, "timeline");
        }
    }

    public static final DependencyRoute TRANSFER_BUILD_TO_GRAPHICS = new DependencyRoute(
            QueueLane.COMPUTE, WorkKind.TRANSFER_BUILD, QueueLane.GRAPHICS, Timeline.BUILD_COMPLETION);
    public static final DependencyRoute GRAPHICS_FRAME_REUSE = new DependencyRoute(
            QueueLane.GRAPHICS, WorkKind.FRAME, QueueLane.GRAPHICS, Timeline.GRAPHICS_COMPLETION);

    private final AtomicLong nextBuildValue = new AtomicLong();
    private final AtomicLong submittedBuildValue = new AtomicLong();
    private final AtomicLong pendingPublishedBuildValue = new AtomicLong();
    private final AtomicLong nextGraphicsValue = new AtomicLong();
    private final AtomicLong latestGraphicsCompletionValue = new AtomicLong();

    /** Reserve a completion value for work recorded on the compute queue's transfer/build lane. */
    public long reserveTransferBuild() {
        return nextBuildValue.incrementAndGet();
    }

    /** Record the highest build timeline value submitted by the existing batched executor. */
    public void markTransferBuildSubmitted(long value) {
        requireReserved(value, nextBuildValue.get(), "build");
        submittedBuildValue.accumulateAndGet(value, Math::max);
    }

    public long submittedTransferBuildValue() {
        return submittedBuildValue.get();
    }

    /** Make completed transfer/build output visible to the next and subsequent graphics frames. */
    public void publishTransferBuild(long value) {
        requireReserved(value, nextBuildValue.get(), "build");
        pendingPublishedBuildValue.accumulateAndGet(value, Math::max);
    }

    /** The coalesced build value waited by graphics; zero means no published dependency. */
    public long publishedTransferBuildWaitValue() {
        // Intentionally retained after use, matching the reference's repeated completed-value wait.
        return pendingPublishedBuildValue.get();
    }

    /** Reserve the completion value only after the caller has attached every required frame wait. */
    public long reserveGraphicsFrameCompletion() {
        return nextGraphicsValue.incrementAndGet();
    }

    /** Record the graphics timeline value signalled after the frame's final legacy consumer. */
    public void completeGraphicsFrame(long value) {
        requireReserved(value, nextGraphicsValue.get(), "graphics");
        latestGraphicsCompletionValue.accumulateAndGet(value, Math::max);
    }

    public long latestGraphicsCompletionValue() {
        return latestGraphicsCompletionValue.get();
    }

    /** Validate a graphics completion token before a cross-frame reuse wait. */
    public long requireGraphicsCompletionValue(long value) {
        requireReserved(value, nextGraphicsValue.get(), "graphics");
        return value;
    }

    private static void requireReserved(long value, long reserved, String kind) {
        if (value <= 0L || value > reserved) {
            throw new IllegalArgumentException(kind + " timeline value was not reserved: " + value);
        }
    }
}
