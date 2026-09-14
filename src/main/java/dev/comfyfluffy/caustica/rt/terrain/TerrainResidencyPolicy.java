package dev.comfyfluffy.caustica.rt.terrain;

/** Pure, hysteretic memory-pressure policy for terrain streaming. */
final class TerrainResidencyPolicy {
    enum State { NORMAL, PRESSURE }

    record Decision(State state, boolean dispatchAllowed) { }

    private State state = State.NORMAL;

    Decision update(long heapUsage, long heapBudget, long safetyHeadroom, long hysteresisMargin) {
        long headroom = Math.max(0L, heapBudget - heapUsage);
        if (state == State.NORMAL && headroom < safetyHeadroom) {
            state = State.PRESSURE;
        } else if (state == State.PRESSURE && headroom > safetyHeadroom + hysteresisMargin) {
            state = State.NORMAL;
        }
        return new Decision(state, state == State.NORMAL);
    }

    State state() {
        return state;
    }

    static long distanceSquared(int x, int y, int z, int px, int py, int pz) {
        long dx = (long) x - px;
        long dy = (long) y - py;
        long dz = (long) z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
