package dev.comfyfluffy.caustica.rt.gpu;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccelerationStructureManagerTest {
    @Test
    void retirementPreservesTokenAndDefersDestructionUntilQueueDelivery() {
        var received = new java.util.concurrent.atomic.AtomicReference<
                dev.comfyfluffy.caustica.rt.RtGpuExecutor.TrackedGraphicsUse>();
        var pending = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var destroyed = new java.util.concurrent.atomic.AtomicInteger();
        var queue = new DeferredDeletionQueue(
                (token, callback) -> { throw new AssertionError("wrong token domain"); },
                (token, callback) -> { received.set(token); pending.set(callback); });
        var manager = new AccelerationStructureManager(queue);
        var token = new dev.comfyfluffy.caustica.rt.RtGpuExecutor.TrackedGraphicsUse();
        Runnable destroy = destroyed::incrementAndGet;

        manager.retire(token, destroy);

        org.junit.jupiter.api.Assertions.assertSame(token, received.get());
        org.junit.jupiter.api.Assertions.assertSame(destroy, pending.get());
        assertEquals(0, destroyed.get());
        pending.get().run();
        assertEquals(1, destroyed.get());
    }

    @Test
    void exposesTheRequiredFacadeOperationsAsDirectDelegates() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/gpu/AccelerationStructureManager.java"));

        assertTrue(source.contains("prepareStaticBlas("));
        assertTrue(source.contains("prepareUpdatableBlas("));
        assertTrue(source.contains("refit("));
        assertTrue(source.contains("compact("));
        assertTrue(source.contains("buildTlas("));
        assertTrue(source.contains("retire("));
        assertTrue(source.contains("RtAccel.prepareTerrainBlas("));
        assertTrue(source.contains("RtAccel.preparePersistentEntityBlasBuild("));
        assertTrue(source.contains("RtAccel.prepareUpdatableEntityBlasBuild("));
        assertTrue(source.contains("RtAccel.refitEntityUpdate("));
        assertTrue(source.contains("RtAccel.prepareTerrainCompaction("));
        assertTrue(source.contains("RtAccel.prepareTlas("));
        assertFalse(source.contains("VK_"));
    }

    @Test
    void productionFrameTlasUsesTheManagerWithoutChangingItsOrderingSeam() throws Exception {
        String context = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtContext.java"));
        String composite = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));

        assertTrue(context.contains("new AccelerationStructureManager(deferredDeletionQueue)"));
        int build = composite.indexOf("ctx.accelerationStructures().buildTlas(");
        int publish = composite.indexOf("active.setTlas(", build);
        int record = composite.indexOf("ctx.accelerationStructures().recordTlas(", publish);
        int barrier = composite.indexOf("VulkanCommandEncoder.memoryBarrier(cmd, stack)", record);

        assertTrue(build >= 0);
        assertTrue(build < publish && publish < record && record < barrier);
        assertEquals(-1, composite.indexOf("RtAccel.prepareTlas("));
        assertEquals(-1, composite.indexOf("RtAccel.recordTlasBuild("));
    }
}
