package dev.comfyfluffy.caustica.rt.frame;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Per-frame coordinator for temporal reset requests. It owns no backend
 * resources and only publishes immutable snapshots supplied by the caller.
 */
public final class TemporalState {
    private int pendingReasons;
    private FrameContext frameContext;
    private long snapshottedFrame = Long.MIN_VALUE;

    public void collect(TemporalResetReason reason) {
        pendingReasons = TemporalResetReason.add(pendingReasons,
                Objects.requireNonNull(reason, "reason"));
    }

    public void snapshot(FrameContext context) {
        Objects.requireNonNull(context, "context");
        if (context.frameIndex() != snapshottedFrame) {
            frameContext = context;
            snapshottedFrame = context.frameIndex();
        }
    }

    public FrameContext snapshot() {
        return frameContext;
    }

    public int pendingReasons() {
        return pendingReasons;
    }

    /** Broadcasts the current request and clears it only after the consumer returns. */
    public void broadcast(Consumer<ResetRequest> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (pendingReasons == TemporalResetReason.none()) {
            return;
        }
        ResetRequest request = new ResetRequest(frameContext, pendingReasons);
        consumer.accept(request);
        pendingReasons = TemporalResetReason.none();
    }

    public record ResetRequest(FrameContext frameContext, int reasons) {
        public ResetRequest {
            Objects.requireNonNull(frameContext, "frameContext");
        }
    }
}
