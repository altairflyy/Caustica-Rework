package dev.comfyfluffy.caustica.rt.lod;

import dev.comfyfluffy.caustica.compat.DistantHorizonsCompat;

/**
 * Provider source for the meshes captured by the existing DH upload mixin.
 *
 * <p>This source owns no GPU resources and does not perform DH meshing. It
 * only exposes the captured DH data through the provider-neutral contract.
 * Voxy selection remains in {@link DistantHorizonsCompat#lodMeshesSnapshot()}.
 * </p>
 */
public final class DhLodMeshSource implements LodMeshSource {
    @Override
    public LodMeshSnapshot snapshot() {
        return new LodMeshSnapshot(DistantHorizonsCompat.dhLodMeshesSnapshot());
    }

    @Override
    public long revision() {
        return DistantHorizonsCompat.dhLodRevision();
    }

    @Override
    public int renderDistanceChunks() {
        return DistantHorizonsCompat.dhRenderDistanceChunks();
    }

    @Override
    public void reset() {
        DistantHorizonsCompat.resetDhCapturedLods();
    }
}
