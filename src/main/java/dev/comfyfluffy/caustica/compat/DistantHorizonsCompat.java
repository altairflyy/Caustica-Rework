package dev.comfyfluffy.caustica.compat;

import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import dev.comfyfluffy.caustica.rt.lod.DhLodMeshSource;
import dev.comfyfluffy.caustica.rt.lod.LodMesh;
import dev.comfyfluffy.caustica.rt.lod.LodProviderSelector;

/** Lightweight, API-independent Distant Horizons hybrid-rendering gate. */
public final class DistantHorizonsCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("distanthorizons");
    private static final byte[] EMPTY_QUADS = new byte[0];
    private static final ConcurrentHashMap<Long, LodMesh> LOD_MESHES = new ConcurrentHashMap<>();
    private static final AtomicLong LOD_REVISION = new AtomicLong();
    private static final AtomicLong MESH_VERSION = new AtomicLong();
    private static final Object ACTIVE_MESH_LOCK = new Object();
    private static final IdentityHashMap<Object, PendingActiveMesh> PENDING_ACTIVE_MESHES = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, BoundActiveMesh> BOUND_ACTIVE_MESHES = new IdentityHashMap<>();
    /** DH meshes observed in this world, retained independently of the current raster frustum. */
    private static final LinkedHashMap<Long, LodMesh> RESIDENT_FAR_MESHES = new LinkedHashMap<>();
    private static volatile List<LodMesh> ACTIVE_FAR_MESHES = List.of();
    private static PassActiveMeshes ACTIVE_OPAQUE_PASS = PassActiveMeshes.empty();
    private static volatile int ACTIVE_RASTER_CONTAINER_COUNT;
    private static volatile int ACTIVE_UNMATCHED_CONTAINER_COUNT;
    /**
     * Captures are scoped to the identity of Minecraft's current ClientLevel. DH section-position keys are
     * not world-unique, and uploads for a newly joined world may arrive before the RT proxy observes the
     * level switch. Keeping this scope here lets the upload hook discard the old world's CPU VBO cache
     * before inserting the first new-world mesh, without later wiping those fresh uploads from RT reset.
     */
    private static final Object WORLD_SCOPE_LOCK = new Object();
    private static volatile Object captureWorld;
    private static final LodProviderSelector PROVIDER_SELECTOR = new LodProviderSelector();

    private DistantHorizonsCompat() {
    }

    /**
     * Captured DH quads are flattened into one tightly packed array per render pass. DH emits 64 bytes
     * per quad; dropping an incomplete tail preserves the old per-buffer decoder semantics while avoiding
     * one byte[] and one List node for every source VBO.
     */
    /** DH quality values relevant to RT LOD selection. */
    public record LodQuality(long signature, int maxDataPointWidth, int horizontalQualityRank,
                             String maxHorizontalResolution, String horizontalQuality) {
    }

    /**
     * Capture a DH upload together with the level wrapper that produced it. Multiplayer worlds do not
     * expose {@code getSinglePlayerLevel()}, so relying on that API loses the dimension metadata needed
     * to place every remote-server LOD mesh. The upload callback already carries the correct wrapper.
     */
    public static void captureLodBuffers(long pos, Object level, List<ByteBuffer> opaque,
                                         List<ByteBuffer> transparent) {
        ensureCurrentWorldScope();
        try {
            // These are the exact list identities emitted by the LodQuadBuilder on this worker thread.
            // Consume their post-merge provenance before DH hands the buffers to its async uploader.
            DhMaterialProvenance.BufferProvenance builtProvenance =
                    DhMaterialProvenance.consumeBuiltBuffers(opaque, transparent);
            LodMesh previous = LOD_MESHES.get(pos);
            // DH can re-submit one or both passes unchanged. Compare source buffers directly against the
            // retained flattened bytes before allocating another multi-megabyte array, and reuse an unchanged
            // pass when only its counterpart was rebuilt.
            boolean sameOpaque = previous != null && quadBuffersEqual(opaque, previous.opaque());
            boolean sameTransparent = previous != null && quadBuffersEqual(transparent, previous.transparent());
            if (sameOpaque && sameTransparent) {
                // The upload lists can be reused for a fresh LodBufferContainer. Rebind that
                // exact future even when the byte content is unchanged.
                synchronized (ACTIVE_MESH_LOCK) {
                    PENDING_ACTIVE_MESHES.put(opaque, new PendingActiveMesh(previous));
                }
                return;
            }
            byte[] opaqueCopy = sameOpaque ? previous.opaque() : copyQuadBuffers(opaque);
            byte[] transparentCopy = sameTransparent ? previous.transparent() : copyQuadBuffers(transparent);

            int originX;
            int originY;
            int originZ;
            int width;
            if (previous != null) {
                // DhSectionPos metadata is immutable for a key. Reusing it avoids four reflective calls on
                // every VBO refresh; world changes clear this cache before the next capture.
                originX = previous.originX();
                originY = previous.originY();
                originZ = previous.originZ();
                width = previous.width();
            } else {
                int[] corner = Api.INSTANCE.minCorner(pos, level);
                originX = corner[0];
                originY = corner[1];
                originZ = corner[2];
                width = corner[3];
            }
            // Globally monotonic so a section that was pruned and later re-created can never collide
            // with a still-published proxy entry that happened to have the same per-key version number.
            long version = MESH_VERSION.incrementAndGet();
            int dataPointWidth = estimateDataPointWidth(opaqueCopy, transparentCopy, width);
            int[] opaqueProvenance = sameOpaque ? previous.opaqueProvenance()
                    : builtProvenance.opaque();
            int[] transparentProvenance = sameTransparent ? previous.transparentProvenance()
                    : builtProvenance.transparent();
            LodMesh captured = new LodMesh(pos, version, originX, originY, originZ, width,
                    dataPointWidth, opaqueCopy, transparentCopy, opaqueProvenance, transparentProvenance);
            synchronized (ACTIVE_MESH_LOCK) {
                PENDING_ACTIVE_MESHES.put(opaque, new PendingActiveMesh(captured));
            }
            if (dhRtRingEnabled()) {
                LOD_MESHES.put(pos, captured);
                LOD_REVISION.incrementAndGet();
            } else if (!LOD_MESHES.isEmpty()) {
                resetDhCapturedLods();
            }
        } catch (Throwable ignored) {
            // DH is optional and changes internals between releases. A failed capture must never break the
            // renderer; the existing mesh (if any) remains usable until a later successful upload.
        }
    }

    public static List<LodMesh> lodMeshesSnapshot() {
        return PROVIDER_SELECTOR.snapshot().meshes();
    }

    /** DH-only snapshot used by {@link DhLodMeshSource}; Voxy selection stays in {@link #lodMeshesSnapshot()}. */
    public static List<LodMesh> dhLodMeshesSnapshot() {
        if (!dhRtRingEnabled()) return List.of();
        ensureCurrentWorldScope();
        // This is DH's exact post-cull draw set, in raster submission order. The main Caustica TLAS
        // consumes these same meshes for primary and secondary rays; historical uploads are never a
        // fallback and no second FAR geometry owner exists.
        return ACTIVE_FAR_MESHES;
    }

    /**
     * Ensure the capture map belongs to the ClientLevel that is active now. This method is called from both
     * the DH upload hook and the RT snapshot path: whichever observes the world transition first performs
     * the clear. Fresh uploads from the new world are therefore never erased by a later RT-world reset.
     */
    private static void ensureCurrentWorldScope() {
        Object currentWorld = Minecraft.getInstance().level;
        if (captureWorld == currentWorld) return;
        synchronized (WORLD_SCOPE_LOCK) {
            if (captureWorld == currentWorld) return;
            LOD_MESHES.clear();
            DhMaterialProvenance.clear();
            synchronized (ACTIVE_MESH_LOCK) {
                PENDING_ACTIVE_MESHES.clear();
                BOUND_ACTIVE_MESHES.clear();
                RESIDENT_FAR_MESHES.clear();
                ACTIVE_FAR_MESHES = List.of();
                ACTIVE_OPAQUE_PASS = PassActiveMeshes.empty();
                ACTIVE_RASTER_CONTAINER_COUNT = 0;
                ACTIVE_UNMATCHED_CONTAINER_COUNT = 0;
            }
            captureWorld = currentWorld;
            LOD_REVISION.incrementAndGet();
        }
    }

    /** Drop captured buffers when disabling the integration or performing final shutdown. */
    public static void clearCapturedLods() {
        PROVIDER_SELECTOR.reset();
        synchronized (ACTIVE_MESH_LOCK) {
            PENDING_ACTIVE_MESHES.clear();
            BOUND_ACTIVE_MESHES.clear();
            RESIDENT_FAR_MESHES.clear();
            ACTIVE_FAR_MESHES = List.of();
            ACTIVE_OPAQUE_PASS = PassActiveMeshes.empty();
            ACTIVE_RASTER_CONTAINER_COUNT = 0;
            ACTIVE_UNMATCHED_CONTAINER_COUNT = 0;
        }
    }

    /** Establish the same world scope before the transformer publishes provenance for a pending upload. */
    static void prepareProvenanceCapture() {
        ensureCurrentWorldScope();
    }

    public static long lodRevision() {
        return PROVIDER_SELECTOR.revision();
    }

    /** DH-only revision used by {@link DhLodMeshSource}. */
    public static long dhLodRevision() {
        if (!dhRtRingEnabled()) return 0L;
        ensureCurrentWorldScope();
        return LOD_REVISION.get();
    }

    /** Clear only DH's captured CPU meshes; Voxy state belongs to its own source. */
    public static void resetDhCapturedLods() {
        synchronized (WORLD_SCOPE_LOCK) {
            if (!LOD_MESHES.isEmpty()) {
                LOD_MESHES.clear();
                LOD_REVISION.incrementAndGet();
            }
            DhMaterialProvenance.clear();
            synchronized (ACTIVE_MESH_LOCK) {
                RESIDENT_FAR_MESHES.clear();
                ACTIVE_OPAQUE_PASS = PassActiveMeshes.empty();
                ACTIVE_FAR_MESHES = List.of();
                ACTIVE_RASTER_CONTAINER_COUNT = 0;
                ACTIVE_UNMATCHED_CONTAINER_COUNT = 0;
            }
            // Adopt the currently visible level. A later real level-identity change still advances the
            // revision through ensureCurrentWorldScope(), while same-world re-uploads can repopulate normally.
            captureWorld = currentClientLevel();
        }
    }

    private static Object currentClientLevel() {
        try {
            return Minecraft.getInstance().level;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long quadByteCount(List<ByteBuffer> buffers) {
        long total = 0L;
        for (ByteBuffer source : buffers) {
            if (source != null) total += source.remaining() & ~63L;
        }
        return total;
    }

    private static boolean quadBuffersEqual(List<ByteBuffer> buffers, byte[] expected) {
        long total = quadByteCount(buffers);
        if (total != expected.length) return false;
        int expectedOffset = 0;
        for (ByteBuffer source : buffers) {
            if (source == null) continue;
            ByteBuffer actual = source.duplicate();
            int bytes = actual.remaining() & ~63;
            if (bytes == 0) continue;
            actual.limit(actual.position() + bytes);
            ByteBuffer expectedSlice = ByteBuffer.wrap(expected, expectedOffset, bytes).slice();
            if (actual.slice().mismatch(expectedSlice) != -1) return false;
            expectedOffset += bytes;
        }
        return true;
    }

    private static byte[] copyQuadBuffers(List<ByteBuffer> buffers) {
        long total = quadByteCount(buffers);
        if (total == 0L) return EMPTY_QUADS;
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Distant Horizons LOD buffer set is too large: " + total);
        }

        byte[] out = new byte[(int) total];
        int offset = 0;
        for (ByteBuffer source : buffers) {
            if (source == null) continue;
            ByteBuffer copy = source.duplicate();
            int bytes = copy.remaining() & ~63;
            if (bytes == 0) continue;
            copy.limit(copy.position() + bytes);
            copy.get(out, offset, bytes);
            offset += bytes;
        }
        return out;
    }

    public static boolean enabled() {
        // Voxy keeps its own enable switch. The Caustica DH switch controls only conversion of DH
        // upload buffers into RT geometry; it must not make the native DH raster disappear.
        return available() && (VoxyCompat.active() || dhRtRingEnabled());
    }

    private record PendingActiveMesh(LodMesh mesh) {
    }

    private record BoundActiveMesh(LodMesh mesh, Object[] wrappers) {
    }

    /** Reflection is isolated to exact object identity; failure can only remove FAR geometry. */
    private static final class RenderContainerApi {
        static final RenderContainerApi INSTANCE = new RenderContainerApi();
        private final Field opaqueWrappers;
        private final Field transparentWrappers;
        private final Method setSize;
        private final Method setGet;

        private RenderContainerApi() {
            try {
                Class<?> container = Class.forName(
                        "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer");
                opaqueWrappers = container.getField("vboOpaqueWrappers");
                transparentWrappers = container.getField("vboTransparentWrappers");
                Class<?> sortedSet = Class.forName(
                        "com.seibel.distanthorizons.core.util.objects.SortedArraySet");
                setSize = sortedSet.getMethod("size");
                setGet = sortedSet.getMethod("get", int.class);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Distant Horizons render-container ABI", e);
            }
        }

        /** DH's SortedArraySet is not java.lang.Iterable in 3.2.0-b. */
        Object[] snapshot(Object containers) {
            if (containers == null) return null;
            if (containers instanceof Iterable<?> iterable) {
                ArrayList<Object> result = new ArrayList<>();
                for (Object value : iterable) result.add(value);
                return result.toArray();
            }
            try {
                int size = ((Number) setSize.invoke(containers)).intValue();
                if (size < 0 || size > 1_000_000) return null;
                Object[] result = new Object[size];
                for (int i = 0; i < size; i++) result[i] = setGet.invoke(containers, i);
                return result;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }

        Object[] wrapperIdentities(Object container) throws IllegalAccessException {
            Object opaque = opaqueWrappers.get(container);
            Object transparent = transparentWrappers.get(container);
            int opaqueCount = opaque == null ? 0 : Array.getLength(opaque);
            int transparentCount = transparent == null ? 0 : Array.getLength(transparent);
            Object[] result = new Object[opaqueCount + transparentCount];
            for (int i = 0; i < opaqueCount; i++) result[i] = Array.get(opaque, i);
            for (int i = 0; i < transparentCount; i++) result[opaqueCount + i] = Array.get(transparent, i);
            return result;
        }

        boolean sameWrappers(Object container, Object[] expected) {
            try {
                Object[] current = wrapperIdentities(container);
                if (current.length != expected.length) return false;
                for (int i = 0; i < current.length; i++) {
                    if (current[i] != expected[i]) return false;
                }
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

    }

    /** DH itself is the sole authority for FAR enable state and distance. */
    public static boolean dhRtRingEnabled() {
        return LOADED && nativeRasterActive() && dhRenderDistanceChunks() > 0;
    }

    /** Whether an optional distant-geometry provider is present, regardless of its Caustica RT switch. */
    private static boolean available() {
        return LOADED || VoxyCompat.active();
    }

    /**
     * Native Vulkan view of DH's authoritative 256xN block-tile atlas.  Zero means that DH has not
     * created the atlas yet (or that a future DH release changed this optional ABI); callers must keep
     * a valid fallback descriptor in that case.
     */
    public static long blockAtlasTextureView() {
        if (!LOADED) return 0L;
        try {
            Object view = BlockAtlasApi.INSTANCE.textureView();
            return view instanceof VulkanGpuTextureView vkView ? vkView.vkImageView() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /** Exact directional shade DH baked into its vertex RGB for the current lodShading mode. */
    public static float[] lodFaceShades() {
        float[] enabled = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};
        if (!LOADED) return new float[]{1f, 1f, 1f, 1f, 1f, 1f};
        try {
            String mode = LodShadingApi.INSTANCE.modeName();
            if ("DISABLED".equals(mode)) return new float[]{1f, 1f, 1f, 1f, 1f, 1f};
            if ("ENABLED".equals(mode)) return enabled;
            var level = Minecraft.getInstance().level;
            if (level == null) return enabled;
            Direction[] directions = {Direction.DOWN, Direction.UP, Direction.NORTH,
                    Direction.SOUTH, Direction.WEST, Direction.EAST};
            float[] result = new float[6];
            for (int i = 0; i < result.length; i++) {
                result[i] = level.cardinalLighting().byFace(directions[i]);
            }
            return result;
        } catch (Throwable ignored) {
            return enabled;
        }
    }

    private static final class LodShadingApi {
        static final LodShadingApi INSTANCE = new LodShadingApi();
        private final Object configEntry;
        private final Method get;

        private LodShadingApi() {
            try {
                Class<?> quality = Class.forName(
                        "com.seibel.distanthorizons.core.config.Config$Client$Advanced$Graphics$Quality");
                configEntry = quality.getField("lodShading").get(null);
                get = configEntry.getClass().getMethod("get");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Distant Horizons LOD-shading ABI", e);
            }
        }

        String modeName() throws ReflectiveOperationException {
            Object value = get.invoke(configEntry);
            return value instanceof Enum<?> mode ? mode.name() : String.valueOf(value);
        }
    }

    private static final class BlockAtlasApi {
        static final BlockAtlasApi INSTANCE = new BlockAtlasApi();
        private final Object atlas;
        private final Method getTextureWrapper;
        private final Method getTextureView;

        private BlockAtlasApi() {
            try {
                Class<?> atlasClass = Class.forName(
                        "com.seibel.distanthorizons.common.render.blaze.wrappers.texture.BlazeBlockTextureAtlas");
                atlas = atlasClass.getField("INSTANCE").get(null);
                getTextureWrapper = atlasClass.getMethod("getTextureWrapper");
                Class<?> textureClass = Class.forName(
                        "com.seibel.distanthorizons.common.render.blaze.wrappers.texture.IDhBlazeTexture");
                getTextureView = textureClass.getMethod("getTextureView");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Distant Horizons block-atlas ABI", e);
            }
        }

        Object textureView() throws ReflectiveOperationException {
            Object wrapper = getTextureWrapper.invoke(atlas);
            return wrapper == null ? null : getTextureView.invoke(wrapper);
        }
    }

    /** Update optional providers on the render thread before the RT proxy observes their revision. */
    public static void tickOptionalSources() {
        VoxyCompat.tick();
    }

    /**
     * Ask DH to discard only its in-memory render/quadtree data. Keep Caustica's last captured CPU meshes
     * as an atomic fallback while DH repopulates its quadtree: clearing both caches at once would allow the
     * first partial upload batch to replace the complete RT proxy and briefly leave large holes. Fresh DH
     * uploads replace these retained entries by key/version and trigger the normal revision rebuild. No
     * saved LOD database or world data is deleted.
     */
    public static boolean reloadRenderDataCache() {
        if (!dhRtRingEnabled()) return false;
        try {
            return ReloadApi.INSTANCE.clearRenderDataCache();
        } catch (Throwable ignored) {
            return false;
        }
    }


    /** Current DH horizontal quality. Changes are polled by the RT proxy to trigger immediate refinement. */
    public static LodQuality lodQuality() {
        if (!enabled()) return new LodQuality(0L, 16, 2, "UNKNOWN", "UNKNOWN");
        if (VoxyCompat.active()) {
            // The set of widths in a Voxy snapshot is streaming availability, not a user quality setting.
            // Treating its transient maximum as configuration made every newly-arrived 2/4/8-block ring
            // cancel thousands of in-flight BLAS builds and restart the complete proxy. Voxy's finest
            // configured representation is always one block; only an actual distance change is a new
            // quality signature.
            int distance = Math.max(1, VoxyCompat.renderDistanceChunks());
            long signature = 0x564F585900000000L ^ ((long) distance << 16);
            return new LodQuality(signature, 1, 4, "VOXY_STREAMED", "DYNAMIC");
        }
        return dhLodQuality();
    }

    /** DH-only quality snapshot for diagnostics; never substitutes Voxy's independent settings. */
    public static LodQuality dhLodQuality() {
        if (!dhRtRingEnabled()) return new LodQuality(0L, 16, 2, "UNKNOWN", "UNKNOWN");
        try {
            return Api.INSTANCE.lodQuality();
        } catch (Throwable ignored) {
            return new LodQuality(0L, 16, 2, "UNKNOWN", "UNKNOWN");
        }
    }

    private static int estimateDataPointWidth(byte[] opaque, byte[] transparent, int fallbackWidth) {
        int[] histogram = new int[16];
        sampleDataPointWidths(opaque, histogram);
        sampleDataPointWidths(transparent, histogram);
        int bestBucket = -1;
        int bestCount = 0;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > bestCount) {
                bestCount = histogram[i];
                bestBucket = i;
            }
        }
        if (bestBucket < 0) return Math.max(1, Integer.highestOneBit(Math.max(1, fallbackWidth)));
        return 1 << bestBucket;
    }

    private static void sampleDataPointWidths(byte[] bytes, int[] histogram) {
        int quads = bytes.length / 64;
        if (quads == 0) return;
        int samples = Math.min(1024, quads);
        for (int sample = 0; sample < samples; sample++) {
            int quad = (int) (((long) sample * quads) / samples) * 64;
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = quad + vertex * 16;
                int x = u16le(bytes, offset);
                int z = u16le(bytes, offset + 4);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            int span = Math.max(maxX - minX, maxZ - minZ);
            if (span <= 0) continue;
            int rounded = Integer.highestOneBit(span);
            if (rounded < span && rounded < (1 << 15)) rounded <<= 1;
            int bucket = Math.min(histogram.length - 1, Integer.numberOfTrailingZeros(rounded));
            histogram[bucket]++;
        }
    }

    private static int u16le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    public static int renderDistanceChunks() {
        if (!enabled()) return 0;
        return PROVIDER_SELECTOR.renderDistanceChunks();
    }

    /** DH-only render distance used by {@link DhLodMeshSource}. */
    public static int dhRenderDistanceChunks() {
        if (!LOADED) return 0;
        try {
            return Api.INSTANCE.renderDistanceChunks();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Observe whether DH produced a current native frame (including its own F6 state). */
    private static boolean nativeRasterActive() {
        if (!LOADED) return false;
        try {
            return RenderApi.INSTANCE.nativeRasterActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Bind the copied upload bytes to the exact LodBufferContainer returned by DH's upload future. */
    public static void completeLodBufferCapture(Object opaqueBuffers, CompletableFuture<?> future) {
        PendingActiveMesh pending;
        synchronized (ACTIVE_MESH_LOCK) {
            pending = PENDING_ACTIVE_MESHES.remove(opaqueBuffers);
        }
        if (pending == null || future == null) return;
        future.whenComplete((container, failure) -> {
            if (failure != null || container == null) return;
            try {
                Object[] wrappers = RenderContainerApi.INSTANCE.wrapperIdentities(container);
                synchronized (ACTIVE_MESH_LOCK) {
                    BOUND_ACTIVE_MESHES.put(container, new BoundActiveMesh(pending.mesh(), wrappers));
                }
            } catch (Throwable ignored) {
                // Optional-version drift means omission, never a fallback to historical captures.
            }
        });
    }

    /** Publish one exact post-cull pass; the transparent pass commits the coherent opaque+transparent frame set. */
    public static void publishActiveRasterContainers(Object exactContainers, boolean transparentPass) {
        Object[] containers = RenderContainerApi.INSTANCE.snapshot(exactContainers);
        if (containers == null) {
            synchronized (ACTIVE_MESH_LOCK) {
                if (!transparentPass) ACTIVE_OPAQUE_PASS = PassActiveMeshes.empty();
            }
            return;
        }
        ArrayList<LodMesh> active = new ArrayList<>();
        int count = 0;
        int unmatched = 0;
        synchronized (ACTIVE_MESH_LOCK) {
            for (Object container : containers) {
                count++;
                BoundActiveMesh bound = BOUND_ACTIVE_MESHES.get(container);
                if (bound == null || !RenderContainerApi.INSTANCE.sameWrappers(container, bound.wrappers())) {
                    unmatched++;
                    continue;
                }
                active.add(bound.mesh());
            }
            PassActiveMeshes pass = new PassActiveMeshes(List.copyOf(active), count, unmatched);
            if (!transparentPass) {
                ACTIVE_OPAQUE_PASS = pass;
                return;
            }

            LinkedHashMap<Long, LodMesh> combined = new LinkedHashMap<>();
            for (LodMesh mesh : ACTIVE_OPAQUE_PASS.meshes) {
                combined.put(mesh.key(), mesh);
            }
            for (LodMesh mesh : pass.meshes) {
                combined.merge(mesh.key(), mesh,
                        (opaque, transparent) -> transparent.version() >= opaque.version() ? transparent : opaque);
            }
            for (LodMesh mesh : combined.values()) {
                RESIDENT_FAR_MESHES.merge(mesh.key(), mesh,
                        (resident, visible) -> visible.version() >= resident.version() ? visible : resident);
            }
            List<LodMesh> published = List.copyOf(RESIDENT_FAR_MESHES.values());
            // Count the containers submitted by DH this frame, not the resident RT set retained across
            // frustum changes. This makes the diagnostic describe actual native-raster activity.
            ACTIVE_RASTER_CONTAINER_COUNT = ACTIVE_OPAQUE_PASS.containers + pass.containers;
            ACTIVE_UNMATCHED_CONTAINER_COUNT = ACTIVE_OPAQUE_PASS.unmatched + pass.unmatched;
            if (!published.equals(ACTIVE_FAR_MESHES)) {
                ACTIVE_FAR_MESHES = published;
                LOD_REVISION.incrementAndGet();
            }
        }
    }

    private record PassActiveMeshes(List<LodMesh> meshes, int containers, int unmatched) {
        static PassActiveMeshes empty() {
            return new PassActiveMeshes(List.of(), 0, 0);
        }
    }

    public static int activeRasterContainerCount() {
        return ACTIVE_RASTER_CONTAINER_COUNT;
    }

    public static int activeUnmatchedContainerCount() {
        return ACTIVE_UNMATCHED_CONTAINER_COUNT;
    }

    public static void forgetActiveContainer(Object container) {
        if (container == null) return;
        synchronized (ACTIVE_MESH_LOCK) {
            BOUND_ACTIVE_MESHES.remove(container);
        }
    }

    /** Isolated from the terrain API so a DH renderer-internal change cannot disable terrain capture. */
    private static final class RenderApi {
        static final RenderApi INSTANCE = new RenderApi();
        private final Field renderParamsField;
        private final Field clientApiInstanceField;
        private final Field rendererDisabledBecauseOfExceptions;
        private final Field validatedField;

        private RenderApi() {
            try {
                Class<?> clientApi = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi");
                clientApiInstanceField = clientApi.getField("INSTANCE");
                rendererDisabledBecauseOfExceptions = clientApi.getField("rendererDisabledBecauseOfExceptions");
                renderParamsField = clientApi.getDeclaredField("RENDER_PARAMS");
                renderParamsField.setAccessible(true);
                Class<?> renderParams = Class.forName("com.seibel.distanthorizons.core.render.RenderParams");
                validatedField = renderParams.getField("hasBeenValidated");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Distant Horizons Blaze renderer", e);
            }
        }

        boolean nativeRasterActive() throws ReflectiveOperationException {
            Object params = renderParamsField.get(null);
            if (params == null || !validatedField.getBoolean(params)) return false;
            Object clientApi = clientApiInstanceField.get(null);
            if (clientApi != null && rendererDisabledBecauseOfExceptions.getBoolean(clientApi)) return false;
            try {
                Class<?> debugging = Class.forName(
                        "com.seibel.distanthorizons.core.config.Config$Client$Advanced$Debugging");
                Field rendererMode = debugging.getField("rendererMode");
                Object entry = rendererMode.get(null);
                Method get = entry == null ? null : entry.getClass().getMethod("get");
                Object mode = get == null ? null : get.invoke(entry);
                return mode == null || !"DISABLED".equals(String.valueOf(mode));
            } catch (ReflectiveOperationException ignored) {
                // The mode field is observation-only. If it drifts, DH's native hook remains authoritative.
                return true;
            }
        }

    }

    /** Public DH render-cache reload API, isolated so optional-version drift cannot disable capture. */
    private static final class ReloadApi {
        static final ReloadApi INSTANCE = new ReloadApi();
        private final Field renderProxyField;
        private final Method clearRenderDataCache;
        private final Field resultSuccess;

        private ReloadApi() {
            try {
                Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
                Class<?> renderProxy = Class.forName(
                        "com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy");
                Class<?> result = Class.forName("com.seibel.distanthorizons.api.objects.DhApiResult");
                renderProxyField = delayed.getField("renderProxy");
                clearRenderDataCache = renderProxy.getMethod("clearRenderDataCache");
                resultSuccess = result.getField("success");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Distant Horizons render reload API", e);
            }
        }

        boolean clearRenderDataCache() throws ReflectiveOperationException {
            Object proxy = renderProxyField.get(null);
            if (proxy == null) return false;
            Object result = clearRenderDataCache.invoke(proxy);
            return result != null && resultSuccess.getBoolean(result);
        }
    }

    /** Small reflective surface kept separate from renderer internals. */
    private static final class Api {
        static final Api INSTANCE = new Api();
        private final Field configsField;
        private final Method graphicsConfig;
        private final Method chunkRenderDistance;
        private final Method maxHorizontalResolution;
        private final Method horizontalQuality;
        private final Method configValue;
        private final Field dataPointWidth;
        private final Method minCornerX;
        private final Method minCornerZ;
        private final Method minHeight;
        private final Method blockWidth;

        private Api() {
            try {
                Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
                configsField = delayed.getField("configs");
                Class<?> level = Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper");
                Class<?> config = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig");
                Class<?> graphics = Class.forName(
                        "com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig");
                Class<?> value = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue");
                graphicsConfig = config.getMethod("graphics");
                chunkRenderDistance = graphics.getMethod("chunkRenderDistance");
                maxHorizontalResolution = graphics.getMethod("maxHorizontalResolution");
                horizontalQuality = graphics.getMethod("horizontalQuality");
                configValue = value.getMethod("getValue");
                Class<?> maxResolution = Class.forName(
                        "com.seibel.distanthorizons.api.enums.config.EDhApiMaxHorizontalResolution");
                dataPointWidth = maxResolution.getField("dataPointWidth");
                Class<?> sectionPos = Class.forName("com.seibel.distanthorizons.core.pos.DhSectionPos");
                minCornerX = sectionPos.getMethod("getMinCornerBlockX", long.class);
                minCornerZ = sectionPos.getMethod("getMinCornerBlockZ", long.class);
                blockWidth = sectionPos.getMethod("getBlockWidth", long.class);
                minHeight = level.getMethod("getMinHeight");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Distant Horizons terrain API", e);
            }
        }

        int[] minCorner(long pos, Object levelHint) throws ReflectiveOperationException {
            int y = Integer.MIN_VALUE;
            if (levelHint != null) {
                try {
                    // Works for both IDhApiSinglePlayerLevelWrapper and IDhApiMultiplayerLevelWrapper.
                    y = ((Number) minHeight.invoke(levelHint)).intValue();
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Some DH releases pass a small internal client-level facade instead of the public API
                    // wrapper. Do not discard the whole remote-server VBO upload because of that detail.
                }
            }
            if (y == Integer.MIN_VALUE) {
                // The Minecraft ClientLevel exists for integrated and dedicated-server connections and is
                // therefore the authoritative fallback when DH's wrapper shape differs between versions.
                var clientLevel = Minecraft.getInstance().level;
                y = clientLevel != null ? clientLevel.getMinY() : -64;
            }
            return new int[]{((Number) minCornerX.invoke(null, pos)).intValue(), y,
                    ((Number) minCornerZ.invoke(null, pos)).intValue(),
                    ((Number) blockWidth.invoke(null, pos)).intValue()};
        }

        int renderDistanceChunks() throws ReflectiveOperationException {
            Object configs = configsField.get(null);
            if (configs == null) return 0;
            Object graphics = graphicsConfig.invoke(configs);
            Object distance = chunkRenderDistance.invoke(graphics);
            return ((Number) configValue.invoke(distance)).intValue();
        }


        LodQuality lodQuality() throws ReflectiveOperationException {
            Object configs = configsField.get(null);
            if (configs == null) return new LodQuality(0L, 16, 2, "UNKNOWN", "UNKNOWN");
            Object graphics = graphicsConfig.invoke(configs);
            Object maxValue = configValue.invoke(maxHorizontalResolution.invoke(graphics));
            Object horizontalValue = configValue.invoke(horizontalQuality.invoke(graphics));
            String maxName = maxValue instanceof Enum<?> value ? value.name() : String.valueOf(maxValue);
            String horizontalName = horizontalValue instanceof Enum<?> value
                    ? value.name() : String.valueOf(horizontalValue);
            int width = maxValue == null ? 16 : Math.max(1, dataPointWidth.getInt(maxValue));
            int rank = horizontalValue instanceof Enum<?> value ? value.ordinal() : 2;
            long signature = ((long) maxName.hashCode() << 32) ^ (horizontalName.hashCode() & 0xFFFFFFFFL);
            return new LodQuality(signature, width, rank, maxName, horizontalName);
        }
    }
}
