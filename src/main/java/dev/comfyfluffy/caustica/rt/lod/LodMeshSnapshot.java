package dev.comfyfluffy.caustica.rt.lod;

import java.util.List;
import java.util.Objects;

/**
 * Immutable view of the provider-neutral meshes captured for one LOD source
 * snapshot.
 *
 * <p>The mesh payloads are the byte arrays captured by the existing DH/Voxy
 * boundary. This type freezes the collection and its ordering without copying
 * or reinterpreting those payloads.</p>
 */
public record LodMeshSnapshot(List<LodMesh> meshes) {
    public LodMeshSnapshot {
        meshes = List.copyOf(Objects.requireNonNull(meshes, "meshes"));
    }
}
