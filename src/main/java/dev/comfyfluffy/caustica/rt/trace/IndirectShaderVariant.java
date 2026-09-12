package dev.comfyfluffy.caustica.rt.trace;

/** Compile-time indirect shader variants; indices match WorldTraceResources' raygen SBT order. */
public enum IndirectShaderVariant {
    GENERIC(1),
    DLSS_RR_NO_NRD(2);

    private final int raygenIndex;
    private static final boolean LEAN_ENABLED = Boolean.parseBoolean(
            System.getProperty("caustica.indirectLean", "true"));

    IndirectShaderVariant(int raygenIndex) {
        this.raygenIndex = raygenIndex;
    }

    public int raygenIndex() {
        return raygenIndex;
    }

    /** NRD requires its per-lobe outputs; DLSS-RR does not, so only that path may use the lean shader. */
    public static IndirectShaderVariant select(boolean dlssRayReconstruction, boolean nrd) {
        return select(dlssRayReconstruction, nrd, LEAN_ENABLED);
    }

    static IndirectShaderVariant select(boolean dlssRayReconstruction, boolean nrd, boolean leanEnabled) {
        return leanEnabled && dlssRayReconstruction && !nrd ? DLSS_RR_NO_NRD : GENERIC;
    }
}
