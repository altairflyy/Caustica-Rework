package dev.comfyfluffy.caustica.rt.lod;

/**
 * Minimal provider-neutral contract for a captured distant-horizon mesh
 * source.
 *
 * <p>Implementations are introduced in the provider tasks that follow this
 * contract. This interface does not own GPU resources or select between
 * providers.</p>
 */
public interface LodMeshSource {
    LodMeshSnapshot snapshot();

    long revision();

    int renderDistanceChunks();

    void reset();
}
