package dev.comfyfluffy.caustica.rt.lod;

import dev.comfyfluffy.caustica.compat.VoxyCompat;

import java.util.List;

/**
 * Provider source for the Caustica Voxy bridge.
 *
 * <p>The bridge is intentionally optional and is not a claim of compatibility
 * with standard Voxy. {@link VoxyCompat} performs the reflective ABI check and
 * returns a clean unavailable state when the Caustica bridge is absent or
 * incompatible.</p>
 */
public final class VoxyBridgeLodMeshSource implements LodMeshSource {
    /** Whether the optional Caustica Voxy bridge passed its ABI discovery. */
    public boolean available() {
        return VoxyCompat.enabled();
    }

    /** Whether the compatible bridge is enabled as the active Voxy source. */
    public boolean active() {
        return VoxyCompat.active();
    }

    @Override
    public LodMeshSnapshot snapshot() {
        return active() ? new LodMeshSnapshot(VoxyCompat.meshes()) : new LodMeshSnapshot(List.of());
    }

    @Override
    public long revision() {
        return VoxyCompat.revision();
    }

    @Override
    public int renderDistanceChunks() {
        return VoxyCompat.renderDistanceChunks();
    }

    @Override
    public void reset() {
        VoxyCompat.reset();
    }
}
