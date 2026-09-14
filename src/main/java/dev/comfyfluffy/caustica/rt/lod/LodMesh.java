package dev.comfyfluffy.caustica.rt.lod;

/** Provider-neutral immutable metadata plus captured quad bytes for one LOD mesh. */
public record LodMesh(long key, long version, int originX, int originY, int originZ, int width,
                      int dataPointWidth, byte[] opaque, byte[] transparent,
                      int[] opaqueProvenance, int[] transparentProvenance) {
    public LodMesh(long key, long version, int originX, int originY, int originZ, int width,
                   int dataPointWidth, byte[] opaque, byte[] transparent) {
        this(key, version, originX, originY, originZ, width, dataPointWidth, opaque, transparent,
                new int[opaque.length / 64], new int[transparent.length / 64]);
    }
}
