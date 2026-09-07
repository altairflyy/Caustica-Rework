package dev.comfyfluffy.caustica.rt.frame;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalStateTest {
    @Test
    void collectsBroadcastsAndClearsOnlyAfterConsumption() {
        TemporalState state = new TemporalState();
        FrameContext frame = frame(4);
        state.snapshot(frame);
        state.collect(TemporalResetReason.WORLD_CHANGE);
        state.collect(TemporalResetReason.MANUAL);

        TemporalState.ResetRequest[] seen = new TemporalState.ResetRequest[1];
        state.broadcast(request -> seen[0] = request);
        assertSame(frame, seen[0].frameContext());
        assertTrue(TemporalResetReason.contains(seen[0].reasons(), TemporalResetReason.WORLD_CHANGE));
        assertTrue(TemporalResetReason.contains(seen[0].reasons(), TemporalResetReason.MANUAL));
        assertEquals(0, state.pendingReasons());
    }

    @Test
    void failedConsumerDoesNotLoseTheRequestAndSnapshotIsOncePerFrame() {
        TemporalState state = new TemporalState();
        FrameContext first = frame(8);
        FrameContext replacement = frame(8);
        state.snapshot(first);
        state.snapshot(replacement);
        assertSame(first, state.snapshot());
        state.collect(TemporalResetReason.RESOLUTION_CHANGE);
        assertThrows(RuntimeException.class, () -> state.broadcast(request -> { throw new RuntimeException(); }));
        assertTrue(TemporalResetReason.contains(state.pendingReasons(), TemporalResetReason.RESOLUTION_CHANGE));
    }

    private static FrameContext frame(long index) {
        return new FrameContext(index, 1.0f / 60.0f,
                new FrameContext.Extent(1, 1), new FrameContext.Extent(1, 1),
                new FrameContext.Camera(0, 0, 0, new Matrix4f()),
                new FrameContext.Camera(0, 0, 0, new Matrix4f()),
                new FrameContext.Jitter(0, 0), new Object(), 0, 0);
    }
}
