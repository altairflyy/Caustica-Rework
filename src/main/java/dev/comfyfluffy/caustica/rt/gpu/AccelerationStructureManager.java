package dev.comfyfluffy.caustica.rt.gpu;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtGpuExecutor;
import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import dev.comfyfluffy.caustica.rt.accel.RtBuffer;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.List;
import java.util.Objects;

/**
 * Runtime facade over the existing {@link RtAccel} implementation.
 *
 * <p>This boundary deliberately contains no Vulkan flags, geometry assembly,
 * resource storage, or lifetime state. The legacy implementation and returned
 * ownership objects remain unchanged.</p>
 */
public final class AccelerationStructureManager {
    private final DeferredDeletionQueue deletionQueue;

    public AccelerationStructureManager(DeferredDeletionQueue deletionQueue) {
        this.deletionQueue = Objects.requireNonNull(deletionQueue, "deletionQueue");
    }

    public RtAccel.PreparedBlas prepareStaticBlas(
            RtContext ctx, RtBuffer positions, int vertexCount, RtBuffer indices,
            int[] bucketTris, RtAccel.OpacityMicromapInput opacityMicromap,
            boolean compact, String label) {
        return RtAccel.prepareTerrainBlas(ctx, positions, vertexCount, indices,
                bucketTris, opacityMicromap, compact, label);
    }

    public RtAccel.PersistentBuild prepareStaticBlas(
            RtContext ctx, long vertexAddress, int vertexCount, long indexAddress,
            int[] bucketTris, String label) {
        return RtAccel.preparePersistentEntityBlasBuild(
                ctx, vertexAddress, vertexCount, indexAddress, bucketTris, label);
    }

    public RtAccel.UpdatableBuild prepareUpdatableBlas(
            RtContext ctx, long vertexAddress, int vertexCount, long indexAddress,
            int[] bucketTris, String label) {
        return RtAccel.prepareUpdatableEntityBlasBuild(
                ctx, vertexAddress, vertexCount, indexAddress, bucketTris, label);
    }

    public RtAccel.PreparedBlas refit(
            RtAccel accel, RtBuffer scratch, long vertexAddress, long indexAddress,
            int vertexCount, int[] bucketTris, String label) {
        return RtAccel.refitEntityUpdate(
                accel, scratch, vertexAddress, indexAddress, vertexCount, bucketTris, label);
    }

    public RtAccel.PreparedTerrainCompaction compact(RtContext ctx, RtAccel.PreparedBlas source) {
        return RtAccel.prepareTerrainCompaction(ctx, source);
    }

    public void recordCompaction(
            RtContext ctx, VkCommandBuffer command, RtAccel.PreparedTerrainCompaction compaction) {
        RtAccel.recordTerrainCompaction(ctx, command, compaction);
    }

    public void finishCompaction(RtAccel.PreparedTerrainCompaction compaction) {
        RtAccel.finishTerrainCompaction(compaction);
    }

    public void destroyCompaction(RtAccel.PreparedTerrainCompaction compaction) {
        RtAccel.destroyTerrainCompaction(compaction);
    }

    public void recordBuilds(RtContext ctx, VkCommandBuffer command, List<RtAccel.PreparedBlas> builds) {
        RtAccel.recordBlasBuilds(ctx, command, builds);
    }

    public void releaseBuildScratch(List<RtAccel.PreparedBlas> builds) {
        RtAccel.freeBlasScratch(builds);
    }

    public void releaseTransientBlas(RtAccel.PreparedBlas blas) {
        RtAccel.releaseEntityBlas(blas);
    }

    public void destroyPersistentBlas(RtAccel accel, RtBuffer backing) {
        RtAccel.destroyEntityAccel(accel, backing);
    }

    public RtAccel.PreparedTlas buildTlas(
            RtContext ctx, List<RtAccel.Instance> baseInstances,
            List<RtAccel.Instance> dynamicInstances, RtAccel.TlasRing ring,
            RtGpuExecutor.GraphicsUse graphicsUse) {
        return RtAccel.prepareTlas(ctx, baseInstances, dynamicInstances, ring, graphicsUse);
    }

    public void recordTlas(RtContext ctx, VkCommandBuffer command, RtAccel.PreparedTlas tlas) {
        RtAccel.recordTlasBuild(ctx, command, tlas);
    }

    public void retire(RtGpuExecutor.GraphicsUse lastUse, Runnable destroy) {
        deletionQueue.retireAfterGraphics(lastUse, destroy);
    }

    public void retire(RtGpuExecutor.TrackedGraphicsUse trackedUse, Runnable destroy) {
        deletionQueue.retireAfterGraphics(trackedUse, destroy);
    }
}
