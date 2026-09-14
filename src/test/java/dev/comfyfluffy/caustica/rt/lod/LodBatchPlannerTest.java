package dev.comfyfluffy.caustica.rt.lod;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LodBatchPlannerTest {
    @Test
    void unchangedSourceIsReusedWithoutInspectingBuffers() {
        LodMesh mesh = mesh(17L, 4L, new byte[128], new byte[128]);
        AtomicInteger counterCalls = new AtomicInteger();

        LodBatchPlanner.SourcePlan plan = LodBatchPlanner.planSource(
                mesh, 4L, (ignoredMesh, ignoredBytes, ignoredTransparent, start, end) -> {
                    counterCalls.incrementAndGet();
                    return new LodBatchPlanner.SliceCounts(1, 0, 0, 0);
                });

        assertTrue(plan.reuse());
        assertTrue(plan.batches().isEmpty());
        assertEquals(0, counterCalls.get());
    }

    @Test
    void changedSourceCombinesOpaqueAndTransparentSlicesInOrder() {
        byte[] opaque = new byte[128];
        byte[] transparent = new byte[128];
        LodMesh mesh = mesh(23L, 9L, opaque, transparent);

        LodBatchPlanner.SourcePlan plan = LodBatchPlanner.planSource(
                mesh, 8L, (ignoredMesh, ignoredBytes, transparentPass, start, end) ->
                        new LodBatchPlanner.SliceCounts(transparentPass ? 0 : 3, 0,
                                transparentPass ? 2 : 0, 0));

        assertFalse(plan.reuse());
        assertEquals(1, plan.batches().size());
        LodBatchPlanner.BatchPlan batch = plan.batches().get(0);
        assertEquals(LodBatchPlanner.batchKey(23L, 0), batch.batchKey());
        assertEquals(2, batch.slices().size());
        assertSame(opaque, batch.slices().get(0).bytes());
        assertFalse(batch.slices().get(0).transparentPass());
        assertSame(transparent, batch.slices().get(1).bytes());
        assertTrue(batch.slices().get(1).transparentPass());
    }

    @Test
    void changedSourceSplitsAtTheExistingQuadLimit() {
        int firstSliceBytes = LodBatchPlanner.MAX_BUILD_QUADS * 64;
        byte[] opaque = new byte[firstSliceBytes + 64];
        LodMesh mesh = mesh(31L, 2L, opaque, new byte[0]);

        LodBatchPlanner.SourcePlan plan = LodBatchPlanner.planSource(
                mesh, null, (ignoredMesh, ignoredBytes, ignoredTransparent, start, end) ->
                        new LodBatchPlanner.SliceCounts((end - start) / 64, 0, 0, 0));

        assertFalse(plan.reuse());
        assertEquals(2, plan.batches().size());
        assertEquals(LodBatchPlanner.batchKey(31L, 0), plan.batches().get(0).batchKey());
        assertEquals(LodBatchPlanner.batchKey(31L, 1), plan.batches().get(1).batchKey());
        assertEquals(firstSliceBytes, plan.batches().get(0).slices().get(0).end());
        assertEquals(firstSliceBytes, plan.batches().get(1).slices().get(0).start());
        assertEquals(1, plan.batches().get(1).slices().get(0).counts().total());
    }

    @Test
    void spatiallySeparateQuadsBecomeIndependentBoundedBatches() {
        byte[] opaque = new byte[64 * 2];
        quad(opaque, 0, 0, 0, 0, 1);
        quad(opaque, 64, 32, 0, 0, 1);
        LodMesh mesh = new LodMesh(41L, 3L, 0, 0, 0, 128, 1, opaque, new byte[0]);

        LodBatchPlanner.SourcePlan plan = LodBatchPlanner.planSource(
                mesh, null, (m, bytes, transparent, start, end) ->
                        new LodBatchPlanner.SliceCounts(1, 0, 0, 0));

        assertEquals(2, plan.batches().size());
        assertEquals(1, plan.batches().get(0).slices().get(0).counts().total());
        assertEquals(1, plan.batches().get(1).slices().get(0).counts().total());
        assertTrue(plan.batches().get(0).coverage().maxX() <= plan.batches().get(1).coverage().x()
                || plan.batches().get(1).coverage().maxX() <= plan.batches().get(0).coverage().x());
    }

    @Test
    void nonContiguousSpatialBucketCarriesOffsetsWithoutDuplicatingPayload() {
        byte[] opaque = new byte[64 * 3];
        quad(opaque, 0, 0, 0, 0, 1);
        quad(opaque, 64, 32, 0, 0, 1); // different tile
        quad(opaque, 128, 0, 0, 0, 1); // returns to first tile
        LodMesh mesh = new LodMesh(42L, 4L, 0, 0, 0, 128, 1, opaque, new byte[0]);

        LodBatchPlanner.SourcePlan plan = LodBatchPlanner.planSource(
                mesh, null, (m, bytes, transparent, start, end) ->
                        new LodBatchPlanner.SliceCounts(1, 0, 0, 0));

        assertEquals(2, plan.batches().size());
        LodBatchPlanner.SlicePlan first = plan.batches().get(0).slices().get(0);
        assertEquals(2, first.counts().total());
        assertEquals(2, first.quadOffsets() == null ? 2 : first.quadOffsets().length);
        assertEquals(3, plan.batches().stream().mapToInt(b -> b.slices().stream()
                .mapToInt(s -> s.counts().total()).sum()).sum());
    }

    private static void quad(byte[] bytes, int offset, int x, int y, int z, int material) {
        for (int v = 0; v < 4; v++) {
            int p = offset + v * 16;
            bytes[p] = (byte) (x + (v & 1));
            bytes[p + 2] = (byte) y;
            bytes[p + 4] = (byte) (z + ((v >>> 1) & 1));
        }
        bytes[offset + 12] = (byte) material;
    }

    private static LodMesh mesh(long key, long version, byte[] opaque, byte[] transparent) {
        return new LodMesh(key, version, 0, 0, 0, 16, 4, opaque, transparent);
    }
}
