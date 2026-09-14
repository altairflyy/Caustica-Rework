package dev.comfyfluffy.caustica.rt.lod;

import dev.comfyfluffy.caustica.rt.lod.LodCoverageResolver.CoverageRect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Plans bounded DH batches with both a quad cap and a conservative XZ footprint cap. */
public final class LodBatchPlanner {
    public static final int MAX_BUILD_QUADS = 131_072;
    private static final int BYTE_STRIDE = 64;
    private static final int VERTEX_STRIDE = 16;
    private static final int MIN_TILE_BLOCKS = 16;
    private static final int MAX_TILE_BLOCKS = 256;

    private LodBatchPlanner() {
    }

    /** Reuse unchanged sources; otherwise classify each source quad into bounded spatial tiles. */
    public static SourcePlan planSource(LodMesh mesh, Long previousVersion, SliceCounter counter) {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(counter, "counter");
        if (previousVersion != null && previousVersion.longValue() == mesh.version()) {
            return new SourcePlan(true, List.of());
        }
        int tileBlocks = spatialTileBlocks(mesh);
        TreeMap<Long, TileBucket> tiles = new TreeMap<>();
        int[] countScratch = new int[4];
        classify(mesh, mesh.opaque(), false, tileBlocks, counter, countScratch, tiles);
        classify(mesh, mesh.transparent(), true, tileBlocks, counter, countScratch, tiles);
        if (tiles.isEmpty()) return new SourcePlan(false, List.of());

        ArrayList<BatchPlan> batches = new ArrayList<>();
        int batchIndex = 0;
        for (TileBucket tile : tiles.values()) {
            ArrayList<SlicePlan> slices = new ArrayList<>();
            appendChunks(mesh, tile, tile.opaque, false, slices);
            appendChunks(mesh, tile, tile.transparent, true, slices);
            ArrayList<SlicePlan> group = new ArrayList<>(2);
            int groupQuads = 0;
            for (SlicePlan slice : slices) {
                int count = slice.counts().total();
                if (!group.isEmpty() && groupQuads + count > MAX_BUILD_QUADS) {
                    batches.add(new BatchPlan(batchKey(mesh.key(), batchIndex++), List.copyOf(group), tile.coverage(mesh.key())));
                    group.clear();
                    groupQuads = 0;
                }
                group.add(slice);
                groupQuads += count;
            }
            if (!group.isEmpty()) {
                batches.add(new BatchPlan(batchKey(mesh.key(), batchIndex++), List.copyOf(group),
                        tile.coverage(mesh.key())));
            }
        }
        return new SourcePlan(false, batches);
    }

    /** One tile per up to sixteen DH data points, capped to keep large sources spatially bounded. */
    public static int spatialTileBlocks(LodMesh mesh) {
        int dataPoint = Math.max(1, mesh.dataPointWidth());
        return Math.max(MIN_TILE_BLOCKS, Math.min(MAX_TILE_BLOCKS, dataPoint * MIN_TILE_BLOCKS));
    }

    public static long batchKey(long sourceKey, int batchIndex) {
        long x = sourceKey ^ (0x9E3779B97F4A7C15L * (batchIndex + 1L));
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private static void classify(LodMesh mesh, byte[] bytes, boolean transparentPass, int tileBlocks,
                                 SliceCounter counter, int[] countScratch, TreeMap<Long, TileBucket> tiles) {
        int quadCount = bytes.length / BYTE_STRIDE;
        for (int quad = 0; quad < quadCount; quad++) {
            int start = quad * BYTE_STRIDE;
            counter.countInto(mesh, bytes, transparentPass, start, start + BYTE_STRIDE, countScratch);
            if (countScratch[0] + countScratch[1] + countScratch[2] + countScratch[3] == 0) continue;
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int vertex = 0; vertex < 4; vertex++) {
                int p = start + vertex * VERTEX_STRIDE;
                int x = u16le(bytes, p) + mesh.originX();
                int y = u16le(bytes, p + 2) + mesh.originY();
                int z = u16le(bytes, p + 4) + mesh.originZ();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
            int tileX = Math.floorDiv(minX - mesh.originX(), tileBlocks);
            int tileZ = Math.floorDiv(minZ - mesh.originZ(), tileBlocks);
            long key = (((long) tileX) << 32) ^ (tileZ & 0xFFFF_FFFFL);
            TileBucket tile = tiles.computeIfAbsent(key, ignored -> new TileBucket());
            tile.add(quad, transparentPass, countScratch, minX, minZ, maxX + 1, maxZ + 1, minY, maxY + 1);
        }
    }

    private static void appendChunks(LodMesh mesh, TileBucket tile, IntArray offsets, boolean transparent,
                                     List<SlicePlan> out) {
        for (int first = 0; first < offsets.size; first += MAX_BUILD_QUADS) {
            int end = Math.min(offsets.size, first + MAX_BUILD_QUADS);
            int[] selected = offsets.copyOfRange(first, end);
            SliceCounts counts = tile.counts(first, end, transparent);
            if (counts.total() == 0) continue;
            int start = selected[0] * BYTE_STRIDE;
            boolean contiguous = true;
            for (int i = 1; i < selected.length; i++) {
                if (selected[i] != selected[i - 1] + 1) { contiguous = false; break; }
            }
            int sliceEnd = contiguous ? selected[selected.length - 1] * BYTE_STRIDE + BYTE_STRIDE : start;
            out.add(new SlicePlan(mesh, transparent ? mesh.transparent() : mesh.opaque(), transparent,
                    start, sliceEnd, counts, contiguous ? null : selected, tile.coverage(mesh.key())));
        }
    }

    private static int u16le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    @FunctionalInterface
    public interface SliceCounter {
        SliceCounts count(LodMesh mesh, byte[] bytes, boolean transparentPass, int start, int end);

        default void countInto(LodMesh mesh, byte[] bytes, boolean transparentPass,
                               int start, int end, int[] out) {
            SliceCounts counts = count(mesh, bytes, transparentPass, start, end);
            out[0] = counts.solid(); out[1] = counts.emissive();
            out[2] = counts.glass(); out[3] = counts.water();
        }
    }

    public record SourcePlan(boolean reuse, List<BatchPlan> batches) {
        public SourcePlan {
            batches = List.copyOf(batches);
            if (reuse && !batches.isEmpty()) throw new IllegalArgumentException("a reused source cannot contain rebuild batches");
        }
    }

    public record BatchPlan(long batchKey, List<SlicePlan> slices, CoverageRect coverage) {
        public BatchPlan {
            slices = List.copyOf(slices);
            Objects.requireNonNull(coverage, "coverage");
        }

        public BatchPlan(long batchKey, List<SlicePlan> slices) {
            this(batchKey, slices, slices.isEmpty()
                    ? new CoverageRect(batchKey, 0, 0, 0, 1)
                    : slices.get(0).coverage());
        }
    }

    public record SlicePlan(LodMesh mesh, byte[] bytes, boolean transparentPass,
                            int start, int end, SliceCounts counts, int[] quadOffsets,
                            CoverageRect coverage) {
        public SlicePlan {
            Objects.requireNonNull(mesh, "mesh");
            Objects.requireNonNull(bytes, "bytes");
            Objects.requireNonNull(counts, "counts");
            Objects.requireNonNull(coverage, "coverage");
            if (quadOffsets != null) quadOffsets = quadOffsets.clone();
        }

        public SlicePlan(LodMesh mesh, byte[] bytes, boolean transparentPass,
                         int start, int end, SliceCounts counts) {
            this(mesh, bytes, transparentPass, start, end, counts, null,
                    new CoverageRect(mesh.key(), mesh.originX(), mesh.originZ(), mesh.width(),
                            mesh.dataPointWidth(), mesh.originY(), Math.addExact(mesh.originY(), mesh.width())));
        }
    }

    public record SliceCounts(int solid, int emissive, int glass, int water) {
        public int total() {
            return Math.addExact(Math.addExact(solid, emissive), Math.addExact(glass, water));
        }

        SliceCounts plus(SliceCounts other) {
            return new SliceCounts(solid + other.solid, emissive + other.emissive,
                    glass + other.glass, water + other.water);
        }
    }

    private static final class TileBucket {
        final IntArray opaque = new IntArray();
        final IntArray transparent = new IntArray();
        final IntCounts opaqueCounts = new IntCounts();
        final IntCounts transparentCounts = new IntCounts();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        void add(int quad, boolean isTransparent, int[] countValues,
                 int qMinX, int qMinZ, int qMaxX, int qMaxZ, int qMinY, int qMaxY) {
            (isTransparent ? transparent : opaque).add(quad);
            (isTransparent ? transparentCounts : opaqueCounts).add(countValues);
            minX = Math.min(minX, qMinX); minZ = Math.min(minZ, qMinZ); minY = Math.min(minY, qMinY);
            maxX = Math.max(maxX, qMaxX); maxZ = Math.max(maxZ, qMaxZ); maxY = Math.max(maxY, qMaxY);
        }

        SliceCounts counts(int from, int to, boolean isTransparent) {
            IntCounts all = isTransparent ? transparentCounts : opaqueCounts;
            return all.range(from, to);
        }

        CoverageRect coverage(long sourceKey) {
            int width = Math.max(1, Math.max(maxX - minX, maxZ - minZ));
            return new CoverageRect(sourceKey, minX, minZ, width, 1, minY, maxY);
        }
    }

    private static final class IntArray {
        int[] values = new int[16];
        int size;
        void add(int value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, size << 1);
            values[size++] = value;
        }
        int get(int index) { return values[index]; }
        int[] copyOfRange(int from, int to) { return java.util.Arrays.copyOfRange(values, from, to); }
    }

    /** Primitive per-quad category counts; avoids retaining a Java record for every classified quad. */
    private static final class IntCounts {
        int[] solid = new int[16];
        int[] emissive = new int[16];
        int[] glass = new int[16];
        int[] water = new int[16];
        int size;

        void add(int[] counts) {
            if (size == solid.length) {
                int next = size << 1;
                solid = java.util.Arrays.copyOf(solid, next);
                emissive = java.util.Arrays.copyOf(emissive, next);
                glass = java.util.Arrays.copyOf(glass, next);
                water = java.util.Arrays.copyOf(water, next);
            }
            solid[size] = counts[0]; emissive[size] = counts[1];
            glass[size] = counts[2]; water[size++] = counts[3];
        }

        SliceCounts range(int from, int to) {
            int s = 0, e = 0, g = 0, w = 0;
            for (int i = from; i < to; i++) {
                s += solid[i]; e += emissive[i]; g += glass[i]; w += water[i];
            }
            return new SliceCounts(s, e, g, w);
        }
    }
}
