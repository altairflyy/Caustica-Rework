package dev.comfyfluffy.caustica.rt.frame;

/** Explicit causes already represented by the legacy temporal reset paths. */
public enum TemporalResetReason {
    WORLD_CHANGE(1 << 0),
    DIMENSION_CHANGE(1 << 1),
    CAMERA_CUT(1 << 2),
    TELEPORT(1 << 3),
    RESOLUTION_CHANGE(1 << 4),
    FOV_CHANGE(1 << 5),
    RESOURCE_RELOAD(1 << 6),
    MATERIAL_GENERATION_CHANGE(1 << 7),
    MANUAL(1 << 8);

    private final int bit;

    TemporalResetReason(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static int none() {
        return 0;
    }

    public static int of(TemporalResetReason reason) {
        return reason.bit;
    }

    public static int add(int reasons, TemporalResetReason reason) {
        return reasons | reason.bit;
    }

    public static boolean contains(int reasons, TemporalResetReason reason) {
        return (reasons & reason.bit) != 0;
    }
}
