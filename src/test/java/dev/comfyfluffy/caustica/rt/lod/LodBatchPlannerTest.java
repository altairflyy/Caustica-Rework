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

    private static LodMesh mesh(long key, long version, byte[] opaque, byte[] transparent) {
        return new LodMesh(key, version, 0, 0, 0, 16, 4, opaque, transparent);
    }
}
