package dev.comfyfluffy.caustica.rt.lod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Plans bounded provider-neutral LOD batches while preserving the legacy
 * reuse and source-buffer slicing rules.
 */
public final class LodBatchPlanner {
    public static final int MAX_BUILD_QUADS = 131_072;
    private static final int BYTE_STRIDE = 64;

    private LodBatchPlanner() {
    }

    /**
     * Reuse all existing batches when the source version is unchanged;
     * otherwise split its opaque and transparent buffers into bounded batches.
     */
    public static SourcePlan planSource(LodMesh mesh, Long previousVersion, SliceCounter counter) {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(counter, "counter");
        if (previousVersion != null && previousVersion.longValue() == mesh.version()) {
            return new SourcePlan(true, List.of());
        }

        ArrayList<SlicePlan> slices = new ArrayList<>();
        appendSlices(mesh, mesh.opaque(), false, counter, slices);
        appendSlices(mesh, mesh.transparent(), true, counter, slices);
        if (slices.isEmpty()) return new SourcePlan(false, List.of());

        ArrayList<BatchPlan> batches = new ArrayList<>();
        ArrayList<SlicePlan> group = new ArrayList<>(2);
        int groupQuads = 0;
        int batchIndex = 0;
        for (SlicePlan slice : slices) {
            int sliceQuads = slice.counts().total();
            if (!group.isEmpty() && groupQuads + sliceQuads > MAX_BUILD_QUADS) {
                batches.add(new BatchPlan(batchKey(mesh.key(), batchIndex++), List.copyOf(group)));
                group.clear();
                groupQuads = 0;
            }
            group.add(slice);
            groupQuads += sliceQuads;
        }
        if (!group.isEmpty()) {
            batches.add(new BatchPlan(batchKey(mesh.key(), batchIndex++), List.copyOf(group)));
        }
        return new SourcePlan(false, batches);
    }

    public static long batchKey(long sourceKey, int batchIndex) {
        long x = sourceKey ^ (0x9E3779B97F4A7C15L * (batchIndex + 1L));
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private static void appendSlices(LodMesh mesh, byte[] bytes, boolean transparentPass,
                                     SliceCounter counter, List<SlicePlan> out) {
        int recordsPerSlice = MAX_BUILD_QUADS;
        for (int firstQuad = 0; firstQuad < bytes.length / BYTE_STRIDE; firstQuad += recordsPerSlice) {
            int start = firstQuad * BYTE_STRIDE;
            int end = (int) Math.min(bytes.length,
                    (long) (firstQuad + recordsPerSlice) * BYTE_STRIDE);
            SliceCounts counts = counter.count(mesh, bytes, transparentPass, start, end);
            if (counts.total() != 0) {
                out.add(new SlicePlan(mesh, bytes, transparentPass, start, end, counts));
            }
        }
    }

    @FunctionalInterface
    public interface SliceCounter {
        SliceCounts count(LodMesh mesh, byte[] bytes, boolean transparentPass, int start, int end);
    }

    public record SourcePlan(boolean reuse, List<BatchPlan> batches) {
        public SourcePlan {
            batches = List.copyOf(batches);
            if (reuse && !batches.isEmpty()) {
                throw new IllegalArgumentException("a reused source cannot contain rebuild batches");
            }
        }
    }

    public record BatchPlan(long batchKey, List<SlicePlan> slices) {
        public BatchPlan {
            slices = List.copyOf(slices);
        }
    }

    public record SlicePlan(LodMesh mesh, byte[] bytes, boolean transparentPass,
                            int start, int end, SliceCounts counts) {
    }

    public record SliceCounts(int solid, int emissive, int glass, int water) {
        public int total() {
            return Math.addExact(Math.addExact(solid, emissive), Math.addExact(glass, water));
        }
    }
}
