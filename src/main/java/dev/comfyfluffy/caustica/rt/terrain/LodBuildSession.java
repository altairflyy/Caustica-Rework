package dev.comfyfluffy.caustica.rt.terrain;

/**
 * Resource-free lifecycle for one progressive LOD replacement.
 *
 * <p>The session describes orchestration only. GPU resources and the
 * published proxy remain owned by {@link RtLodTerrain}.</p>
 */
public final class LodBuildSession {
    public enum State {
        PLANNED,
        PACKING,
        GPU_BUILDING,
        CHECKPOINT_READY,
        PUBLISHED,
        FINAL,
        CANCELLED
    }

    private State state = State.PLANNED;

    public State state() {
        return state;
    }

    public void beginPacking() {
        require(State.PLANNED, State.CHECKPOINT_READY, State.PUBLISHED);
        state = State.PACKING;
    }

    public void beginGpuBuilding() {
        require(State.PACKING);
        state = State.GPU_BUILDING;
    }

    public void markCheckpointReady() {
        require(State.GPU_BUILDING);
        state = State.CHECKPOINT_READY;
    }

    public void publishCheckpoint() {
        require(State.CHECKPOINT_READY);
        state = State.PUBLISHED;
    }

    public void publishFinal() {
        require(State.PLANNED, State.CHECKPOINT_READY, State.PUBLISHED);
        state = State.FINAL;
    }

    public void cancel() {
        if (state != State.FINAL) state = State.CANCELLED;
    }

    private void require(State... allowed) {
        for (State candidate : allowed) {
            if (state == candidate) return;
        }
        throw new IllegalStateException("Invalid LOD build session transition from " + state);
    }
}
