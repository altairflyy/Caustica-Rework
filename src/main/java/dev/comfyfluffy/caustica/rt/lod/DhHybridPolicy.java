package dev.comfyfluffy.caustica.rt.lod;

/** Pure distance policy for the optional Caustica DH RT ring. */
public final class DhHybridPolicy {
    private DhHybridPolicy() {
    }

    /**
     * Returns the effective DH RT radius in chunks. A zero result means that no DH geometry may enter
     * Caustica's BLAS/TLAS. The provider distance is only an availability bound; it never expands the
     * user-configured cap.
     */
    public static int rtDistanceChunks(boolean enabled, int configuredChunks,
                                       int providerDistanceChunks, int hardMaxChunks) {
        if (!enabled || configuredChunks <= 0 || providerDistanceChunks <= 0 || hardMaxChunks <= 0) {
            return 0;
        }
        return Math.min(Math.min(configuredChunks, providerDistanceChunks), hardMaxChunks);
    }
}
