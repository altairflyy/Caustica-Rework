package dev.comfyfluffy.caustica.rt.terrain;

/** Pure hysteresis controller for producer-level background AS pacing. */
final class BackgroundAsPacingPolicy {
    enum State { NORMAL, GPU_BUSY }

    record Decision(State state) {
        boolean busy() {
            return state == State.GPU_BUSY;
        }
    }

    private State state = State.NORMAL;
    private long belowExitSinceNanos;

    Decision update(double totalGpuMillis, long nowNanos, double busyEnterMillis,
                    double busyExitMillis, long resumeStableNanos) {
        if (!Double.isFinite(totalGpuMillis)) {
            return new Decision(state);
        }
        if (state == State.NORMAL) {
            if (totalGpuMillis >= busyEnterMillis) {
                state = State.GPU_BUSY;
                belowExitSinceNanos = 0L;
            }
            return new Decision(state);
        }
        if (totalGpuMillis > busyExitMillis) {
            belowExitSinceNanos = 0L;
            return new Decision(state);
        }
        if (belowExitSinceNanos == 0L) {
            belowExitSinceNanos = nowNanos;
        }
        if (nowNanos - belowExitSinceNanos >= resumeStableNanos) {
            state = State.NORMAL;
            belowExitSinceNanos = 0L;
        }
        return new Decision(state);
    }

    State state() {
        return state;
    }
}
