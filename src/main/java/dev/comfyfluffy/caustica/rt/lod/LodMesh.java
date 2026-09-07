package dev.comfyfluffy.caustica.rt.lod;

/** Provider-neutral immutable metadata plus captured quad bytes for one LOD mesh. */
public record LodMesh(long key, long version, int originX, int originY, int originZ, int width,
                      int dataPointWidth, byte[] opaque, byte[] transparent) {
}
