package dev.comfyfluffy.caustica.rt.proxy;

import dev.comfyfluffy.caustica.compat.DistantHorizonsCompat;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtGpuExecutor;
import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import dev.comfyfluffy.caustica.rt.accel.RtBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.KHRRayTracingPipeline;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated far-field heightfield proxy for Distant Horizons water reflections.
 *
 * <p>Divides the world into 512x512 block tiles with a 32-block sampling step
 * (16x16 cells, 17x17 = 289 vertices, 512 triangles per tile). A bounded ring
 * of resident tiles (radius 7 tiles ~= 177 tiles, max <= 256) is maintained around
 * the camera. The shared index buffer serves all tiles. Heights are extracted
 * directly on DH VBO uploads and temporary buffers are immediately discarded.</p>
 */
public final class DhFarFieldProxy {
    private static final DhFarFieldProxy INSTANCE = new DhFarFieldProxy();

    public static final int TILE_SIZE = 512;
    public static final int SAMPLE_STEP = 32;
    public static final int CELLS_PER_AXIS = 16;
    public static final int VERTS_PER_AXIS = 17;
    public static final int VERTS_PER_TILE = VERTS_PER_AXIS * VERTS_PER_AXIS; // 289
    public static final int TRIS_PER_TILE = CELLS_PER_AXIS * CELLS_PER_AXIS * 2; // 512
    public static final int INDICES_PER_TILE = TRIS_PER_TILE * 3; // 1536
    public static final int RADIUS_TILES = 7;
    public static final int MAX_TILES = 256;
    public static final float BASE_HEIGHT = 62.0f;

    private final ConcurrentHashMap<Long, Tile> residentTiles = new ConcurrentHashMap<>();
    private final RtAccel.TlasRing tlasRing = new RtAccel.TlasRing();
    private final List<RtAccel.PreparedBlas> pendingBlasBuilds = new ArrayList<>();

    private RtContext ownerContext;
    private RtBuffer sharedIndexBuffer;
    private boolean initialized;
    private int lastCenterTileX = Integer.MAX_VALUE;
    private int lastCenterTileZ = Integer.MAX_VALUE;
    private volatile int lastInstanceCount;

    public static DhFarFieldProxy get() {
        return INSTANCE;
    }

    private DhFarFieldProxy() {
    }

    public static long tileKey(int tileX, int tileZ) {
        return (((long) tileX) << 32) | (tileZ & 0xFFFFFFFFL);
    }

    public synchronized void init(RtContext ctx) {
        if (initialized) return;
        long indexBytes = (long) INDICES_PER_TILE * Integer.BYTES;
        sharedIndexBuffer = ctx.createBuffer(indexBytes,
                KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
                true, "dh far-field proxy shared indices");

        IntBuffer ib = MemoryUtil.memIntBuffer(sharedIndexBuffer.mapped, INDICES_PER_TILE);
        for (int gz = 0; gz < CELLS_PER_AXIS; gz++) {
            for (int gx = 0; gx < CELLS_PER_AXIS; gx++) {
                int v00 = gz * VERTS_PER_AXIS + gx;
                int v10 = gz * VERTS_PER_AXIS + (gx + 1);
                int v01 = (gz + 1) * VERTS_PER_AXIS + gx;
                int v11 = (gz + 1) * VERTS_PER_AXIS + (gx + 1);
                ib.put(v00).put(v01).put(v10);
                ib.put(v10).put(v01).put(v11);
            }
        }
        sharedIndexBuffer.flush();
        ownerContext = ctx;
        initialized = true;
    }

    /**
     * Bounded height extraction callback called on DH VBO uploads.
     * Temporary quad byte buffers are sampled directly and not retained.
     */
    public void onLodBuffers(long pos, Object level, List<ByteBuffer> opaque, List<ByteBuffer> transparent) {
        if (opaque == null || opaque.isEmpty()) return;
        int[] corner = DistantHorizonsCompat.minCorner(pos, level);
        if (corner == null) return;
        int originX = corner[0];
        int originY = corner[1];
        int originZ = corner[2];

        sampleBuffers(opaque, originX, originY, originZ);
        if (transparent != null && !transparent.isEmpty()) {
            sampleBuffers(transparent, originX, originY, originZ);
        }
    }

    private void sampleBuffers(List<ByteBuffer> buffers, int originX, int originY, int originZ) {
        for (ByteBuffer buf : buffers) {
            if (buf == null) continue;
            ByteBuffer slice = buf.duplicate();
            slice.order(ByteOrder.LITTLE_ENDIAN);
            int remaining = slice.remaining() & ~63;
            int base = slice.position();

            for (int offset = base; offset < base + remaining; offset += 64) {
                short x0 = slice.getShort(offset);
                short y0 = slice.getShort(offset + 2);
                short z0 = slice.getShort(offset + 4);

                short x1 = slice.getShort(offset + 16);
                short y1 = slice.getShort(offset + 18);
                short z1 = slice.getShort(offset + 20);

                short x2 = slice.getShort(offset + 32);
                short y2 = slice.getShort(offset + 34);
                short z2 = slice.getShort(offset + 36);

                short x3 = slice.getShort(offset + 48);
                short y3 = slice.getShort(offset + 50);
                short z3 = slice.getShort(offset + 52);

                int minX = Math.min(Math.min(x0, x1), Math.min(x2, x3)) + originX;
                int maxX = Math.max(Math.max(x0, x1), Math.max(x2, x3)) + originX;
                int minZ = Math.min(Math.min(z0, z1), Math.min(z2, z3)) + originZ;
                int maxZ = Math.max(Math.max(z0, z1), Math.max(z2, z3)) + originZ;
                int maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3)) + originY;

                if (maxY <= BASE_HEIGHT) continue;

                int tMinX = Math.floorDiv(minX, TILE_SIZE);
                int tMaxX = Math.floorDiv(maxX, TILE_SIZE);
                int tMinZ = Math.floorDiv(minZ, TILE_SIZE);
                int tMaxZ = Math.floorDiv(maxZ, TILE_SIZE);

                for (int tx = tMinX; tx <= tMaxX; tx++) {
                    for (int tz = tMinZ; tz <= tMaxZ; tz++) {
                        Tile tile = residentTiles.get(tileKey(tx, tz));
                        if (tile != null) {
                            tile.updateHeight(minX, maxX, minZ, maxZ, maxY);
                        }
                    }
                }
            }
        }
    }

    private synchronized void updateTilesAround(
            RtContext ctx, int centerTileX, int centerTileZ, RtGpuExecutor.GraphicsUse graphicsUse) {
        if (centerTileX == lastCenterTileX && centerTileZ == lastCenterTileZ) return;
        lastCenterTileX = centerTileX;
        lastCenterTileZ = centerTileZ;

        int maxRadiusSq = RADIUS_TILES * RADIUS_TILES + 1;
        residentTiles.entrySet().removeIf(entry -> {
            Tile t = entry.getValue();
            int dx = t.tileX - centerTileX;
            int dz = t.tileZ - centerTileZ;
            if (dx * dx + dz * dz > maxRadiusSq) {
                t.retire(ctx, graphicsUse);
                return true;
            }
            return false;
        });

        for (int dz = -RADIUS_TILES; dz <= RADIUS_TILES; dz++) {
            for (int dx = -RADIUS_TILES; dx <= RADIUS_TILES; dx++) {
                if (dx * dx + dz * dz <= maxRadiusSq && residentTiles.size() < MAX_TILES) {
                    int tx = centerTileX + dx;
                    int tz = centerTileZ + dz;
                    long key = tileKey(tx, tz);
                    residentTiles.computeIfAbsent(key, k -> {
                        Tile tile = new Tile(tx, tz);
                        tile.ensureBuffer(ctx);
                        return tile;
                    });
                }
            }
        }
    }

    /**
     * Updates height buffers, builds pending BLASes, and builds the dedicated TLAS for the current frame.
     */
    public synchronized RtAccel.PreparedTlas updateAndBuildTlas(
            RtContext ctx, VkCommandBuffer cmd, double camX, double camY, double camZ,
            RtGpuExecutor.GraphicsUse graphicsUse) {
        if (!initialized) init(ctx);

        int centerTileX = Math.floorDiv((int) Math.floor(camX), TILE_SIZE);
        int centerTileZ = Math.floorDiv((int) Math.floor(camZ), TILE_SIZE);
        updateTilesAround(ctx, centerTileX, centerTileZ, graphicsUse);

        for (Tile tile : residentTiles.values()) {
            if (tile.dirty) {
                tile.ensureBuffer(ctx);
                tile.uploadHeights();
                if (tile.blas != null) {
                    RtAccel staleBlas = tile.blas;
                    ctx.accelerationStructures().retire(graphicsUse,
                            () -> ctx.accelerationStructures().destroyOwnedBlas(staleBlas));
                    tile.blas = null;
                    tile.blasReady = false;
                }
            }
            if (tile.blas == null) {
                tile.ensureBuffer(ctx);
                RtAccel.PreparedBlas prepared = RtAccel.prepareTrianglesBlas(
                        ctx, tile.vertexBuffer, VERTS_PER_TILE, sharedIndexBuffer,
                        INDICES_PER_TILE, true, "dh proxy tile (" + tile.tileX + "," + tile.tileZ + ")");
                tile.blas = prepared.accel;
                tile.blasReady = true;
                pendingBlasBuilds.add(prepared);
            }
        }

        if (!pendingBlasBuilds.isEmpty()) {
            RtAccel.recordBlasBuilds(ctx, cmd, pendingBlasBuilds);
            List<RtAccel.PreparedBlas> toRetire = new ArrayList<>(pendingBlasBuilds);
            pendingBlasBuilds.clear();
            ctx.accelerationStructures().retire(graphicsUse,
                    () -> ctx.accelerationStructures().releaseBuildScratch(toRetire));
            accelerationStructureBuildBarrier(cmd);
        }

        List<RtAccel.Instance> instances = new ArrayList<>(residentTiles.size());
        for (Tile tile : residentTiles.values()) {
            if (tile.blas == null || !tile.blasReady) continue;
            float relX = (float) (tile.tileX * TILE_SIZE - camX);
            float relY = (float) (-camY);
            float relZ = (float) (tile.tileZ * TILE_SIZE - camZ);
            float[] transform = {
                    1.0f, 0.0f, 0.0f, relX,
                    0.0f, 1.0f, 0.0f, relY,
                    0.0f, 0.0f, 1.0f, relZ
            };
            instances.add(new RtAccel.Instance(transform, tile.blas.deviceAddress, 0, 0x01));
        }

        lastInstanceCount = instances.size();
        if (instances.isEmpty()) return null;

        RtAccel.PreparedTlas preparedTlas = RtAccel.prepareTlas(ctx, instances, List.of(), tlasRing, graphicsUse);
        RtAccel.recordTlasBuild(ctx, cmd, preparedTlas);
        tlasToRayTracingBarrier(cmd);
        return preparedTlas;
    }

    private static void accelerationStructureBuildBarrier(VkCommandBuffer cmd) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .srcAccessMask(KHRSynchronization2.VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
                    .dstStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .dstAccessMask(KHRSynchronization2.VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR);
            VkDependencyInfo dep = VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(cmd, dep);
        }
    }

    private static void tlasToRayTracingBarrier(VkCommandBuffer cmd) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .srcAccessMask(KHRSynchronization2.VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR)
                    .dstStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR)
                    .dstAccessMask(KHRSynchronization2.VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR);
            VkDependencyInfo dep = VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(cmd, dep);
        }
    }

    public synchronized void destroy() {
        if (!initialized) return;
        RtContext ctx = Objects.requireNonNull(ownerContext, "initialized DH proxy context");
        for (Tile tile : residentTiles.values()) {
            tile.destroy(ctx);
        }
        residentTiles.clear();
        if (sharedIndexBuffer != null) {
            sharedIndexBuffer.destroy();
            sharedIndexBuffer = null;
        }
        tlasRing.destroy();
        ownerContext = null;
        initialized = false;
        lastCenterTileX = Integer.MAX_VALUE;
        lastCenterTileZ = Integer.MAX_VALUE;
    }

    public int activeTileCount() {
        return residentTiles.size();
    }

    public int blasCount() {
        return residentTiles.size();
    }

    public int lastInstanceCount() {
        return lastInstanceCount;
    }

    public long vertexIndexBytes() {
        long indices = sharedIndexBuffer != null ? sharedIndexBuffer.size : 0L;
        long vertices = 0L;
        for (Tile t : residentTiles.values()) {
            if (t.vertexBuffer != null) vertices += t.vertexBuffer.size;
        }
        return indices + vertices;
    }

    public long blasBytes() {
        long total = 0L;
        for (Tile t : residentTiles.values()) {
            if (t.blas != null) total += t.blas.backingSize();
        }
        return total;
    }

    public long totalAllocatedBytes() {
        return vertexIndexBytes() + blasBytes();
    }

    public static final class Tile {
        final int tileX;
        final int tileZ;
        final float[] heights = new float[VERTS_PER_TILE];
        RtBuffer vertexBuffer;
        RtAccel blas;
        boolean dirty;
        boolean blasReady;

        Tile(int tileX, int tileZ) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            Arrays.fill(heights, BASE_HEIGHT);
            dirty = true;
        }

        void ensureBuffer(RtContext ctx) {
            if (vertexBuffer == null) {
                vertexBuffer = ctx.createBuffer((long) VERTS_PER_TILE * 3L * Float.BYTES,
                        KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
                        true, "dh proxy tile (" + tileX + "," + tileZ + ") vertices");
                uploadHeights();
            }
        }

        void uploadHeights() {
            FloatBuffer fb = MemoryUtil.memFloatBuffer(vertexBuffer.mapped, VERTS_PER_TILE * 3);
            for (int gz = 0; gz < VERTS_PER_AXIS; gz++) {
                for (int gx = 0; gx < VERTS_PER_AXIS; gx++) {
                    fb.put(gx * (float) SAMPLE_STEP);
                    fb.put(heights[gz * VERTS_PER_AXIS + gx]);
                    fb.put(gz * (float) SAMPLE_STEP);
                }
            }
            vertexBuffer.flush();
            dirty = false;
        }

        synchronized void updateHeight(int minX, int maxX, int minZ, int maxZ, int maxY) {
            int tileWorldX = tileX * TILE_SIZE;
            int tileWorldZ = tileZ * TILE_SIZE;
            int gx0 = Math.max(0, Math.min(CELLS_PER_AXIS, (minX - tileWorldX) / SAMPLE_STEP));
            int gx1 = Math.max(0, Math.min(CELLS_PER_AXIS, (maxX - tileWorldX + SAMPLE_STEP - 1) / SAMPLE_STEP));
            int gz0 = Math.max(0, Math.min(CELLS_PER_AXIS, (minZ - tileWorldZ) / SAMPLE_STEP));
            int gz1 = Math.max(0, Math.min(CELLS_PER_AXIS, (maxZ - tileWorldZ + SAMPLE_STEP - 1) / SAMPLE_STEP));
            boolean changed = false;
            float y = (float) maxY;
            for (int gz = gz0; gz <= gz1; gz++) {
                for (int gx = gx0; gx <= gx1; gx++) {
                    int idx = gz * VERTS_PER_AXIS + gx;
                    if (y > heights[idx]) {
                        heights[idx] = y;
                        changed = true;
                    }
                }
            }
            if (changed) {
                dirty = true;
            }
        }

        void retire(RtContext ctx, RtGpuExecutor.GraphicsUse graphicsUse) {
            RtAccel retiredBlas = blas;
            RtBuffer retiredVertexBuffer = vertexBuffer;
            blas = null;
            blasReady = false;
            vertexBuffer = null;
            ctx.accelerationStructures().retire(graphicsUse, () -> {
                if (retiredBlas != null) {
                    ctx.accelerationStructures().destroyOwnedBlas(retiredBlas);
                }
                if (retiredVertexBuffer != null) {
                    retiredVertexBuffer.destroy();
                }
            });
        }

        void destroy(RtContext ctx) {
            if (blas != null) {
                ctx.accelerationStructures().destroyOwnedBlas(blas);
                blas = null;
                blasReady = false;
            }
            if (vertexBuffer != null) {
                vertexBuffer.destroy();
                vertexBuffer = null;
            }
        }
    }
}
