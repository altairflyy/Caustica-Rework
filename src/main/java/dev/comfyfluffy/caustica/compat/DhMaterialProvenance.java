package dev.comfyfluffy.caustica.compat;

import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.material.RtMaterialRegistry;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carries the representative DH block/biome identity to the final CPU FAR packing step.
 *
 * <p>The DH vertex ABI remains untouched.  A compact integer is kept beside each final quad in
 * {@code LodMesh}; the registry owns only primitive reference luminances and immutable identity
 * strings and immutable registry block states, never DH sources, wrappers, columns, VBOs or world objects.</p>
 */
public final class DhMaterialProvenance {
    public static final int UNKNOWN = 0;
    private static final int OUTSIDE_GRID = -1;
    private static final int NO_RENDER_CELL = -2;
    private static final int COLUMN_WIDTH = 64;
    /** One DH section build transforms one center plus at most four culling neighbours. */
    private static final int MAX_THREAD_GRIDS = 8;
    private static final int MAX_SOURCES = 32768;
    private static final int MAX_GAIN_ENTRIES = 65536;
    /** EDhApiBlockMaterial.LEAVES in DH's stable packed-vertex ABI. */
    private static final int DH_MATERIAL_LEAVES = 1;
    private static final float[] SRGB_TO_LINEAR = makeSrgbLut();
    private static final Object REGISTRY_LOCK = new Object();
    private static final ConcurrentHashMap<SourceKey, Integer> IDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, SourceEntry> SOURCES = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ThreadLocal<CaptureState> CAPTURE_STATE = ThreadLocal.withInitial(CaptureState::new);
    private static final ConcurrentHashMap<Long, Float> GAINS = new ConcurrentHashMap<>();
    private static volatile boolean loggedFailure;

    private DhMaterialProvenance() { }

    /** Called from the DH transformer RETURN hook, before the full-data identity is discarded. */
    public static void capture(Object fullSource, Object levelWrapper, Object renderSource) {
        if (fullSource == null || levelWrapper == null || renderSource == null) return;
        try {
            DistantHorizonsCompat.prepareProvenanceCapture();
            Access a = Access.INSTANCE;
            int detailWidth = 1 << ((Number) a.fullDetail.invoke(fullSource)).intValue();
            int slices = Math.max(1, a.renderMaxSlices.getInt(renderSource));
            int length = COLUMN_WIDTH * COLUMN_WIDTH * slices;
            int[] ids = new int[length];
            short[] yMin = new short[length];
            short[] yMax = new short[length];
            Object mapping = a.fullMapping.get(fullSource);
            for (int x = 0; x < COLUMN_WIDTH; x++) {
                for (int z = 0; z < COLUMN_WIDTH; z++) {
                    Object column = a.fullColumn.invoke(fullSource, x, z);
                    for (int slice = 0; slice < slices; slice++) {
                        long renderPoint = ((Number) a.renderPoint.invoke(renderSource, x, z, slice)).longValue();
                        if (!((Boolean) a.renderExists.invoke(null, renderPoint))) break;
                        int lo = ((Number) a.renderYMin.invoke(null, renderPoint)).intValue();
                        int hi = ((Number) a.renderYMax.invoke(null, renderPoint)).intValue();
                        if (hi <= lo) break;
                        int index = ((x * COLUMN_WIDTH + z) * slices) + slice;
                        yMin[index] = (short) lo;
                        yMax[index] = (short) hi;
                        long fullPoint = a.pointAt(column, hi - 1);
                        if (fullPoint == 0L) continue;
                        int sourceId = ((Number) a.fullId.invoke(null, fullPoint)).intValue();
                        Object block = a.blockForId.invoke(mapping, sourceId);
                        Object biome = a.biomeForId.invoke(mapping, sourceId);
                        if (block == null || biome == null || (Boolean) a.blockAir.invoke(block)) continue;
                        ids[index] = sourceId(block, biome, fullSource, levelWrapper,
                                x, z, hi - 1, detailWidth);
                    }
                }
            }
            ProvenanceGrid grid = new ProvenanceGrid(detailWidth, slices, ids, yMin, yMax,
                    a.faceShades(levelWrapper));
            CaptureState state = CAPTURE_STATE.get();
            if (state.transformed.size() >= MAX_THREAD_GRIDS) state.dropTransformed();
            state.transformed.add(new RenderGrid(grid));
        } catch (Throwable failure) {
            logFailureOnce("capture", failure);
        }
    }

    /** Starts the exact no-argument LodRenderSection build lifecycle on its DH worker thread. */
    public static void beginBuild() {
        CaptureState state = CAPTURE_STATE.get();
        state.dropTransformed();
        state.builderGrid = null;
        state.pending = null;
    }

    /** DH 3.2.0-b transforms the center source first; later entries are neighbour-only culling sources. */
    public static void bindBuilder() {
        CaptureState state = CAPTURE_STATE.get();
        ProvenanceGrid selected = state.transformed.isEmpty() ? null : state.transformed.get(0).grid;
        state.dropTransformed();
        state.builderGrid = selected;
        state.pending = null;
    }

    /** Resolve final post-merge quads while the exact builder and buffer-list identities still exist. */
    public static void captureBuiltBuffers(List<ByteBuffer> buffers, boolean transparent) {
        CaptureState state = CAPTURE_STATE.get();
        ProvenanceGrid grid = state.builderGrid;
        if (grid == null || buffers == null) {
            return;
        }
        int[] ids = quadIds(grid, buffers);
        if (state.pending == null) state.pending = new PendingBuffers();
        state.pending.set(buffers, ids, transparent);
        if (transparent) state.builderGrid = null;
    }

    /** Consume the provenance arrays for the exact two lists passed to DH's upload method. */
    public static BufferProvenance consumeBuiltBuffers(List<ByteBuffer> opaque, List<ByteBuffer> transparent) {
        CaptureState state = CAPTURE_STATE.get();
        PendingBuffers pending = state.pending;
        state.pending = null;
        state.builderGrid = null;
        state.dropTransformed();
        int[] opaqueIds = pending == null ? null : pending.ids(opaque, false);
        int[] transparentIds = pending == null ? null : pending.ids(transparent, true);
        if (opaqueIds == null) opaqueIds = missingLifecycle(opaque);
        if (transparentIds == null) transparentIds = missingLifecycle(transparent);
        return new BufferProvenance(opaqueIds, transparentIds);
    }

    /** Cached data-driven scalar for the exact source identity, face and DH texture tile. */
    public static float gain(int provenanceId, int face, int textureTileId) {
        if (provenanceId == UNKNOWN || face < 0 || face >= 6) return 1.0f;
        SourceEntry source = SOURCES.get(provenanceId);
        if (source == null || !(source.referenceY[face] > 0.0f)) return 1.0f;
        long key = ((long) provenanceId << 32) | ((long) (face & 0xFF) << 16)
                | (textureTileId & 0xFFFFL);
        Float cached = GAINS.get(key);
        if (cached != null) return cached;
        LinearRgb far = reconstructedDhRgb(textureTileId, source.dhR, source.dhG, source.dhB);
        float computed = gainForTest(source.referenceY[face], luminance(far.r, far.g, far.b));
        if (GAINS.size() >= MAX_GAIN_ENTRIES) GAINS.clear();
        Float raced = GAINS.putIfAbsent(key, computed);
        return raced != null ? raced : computed;
    }

    public static void clear() {
        CAPTURE_STATE.remove();
        IDS.clear();
        SOURCES.clear();
        GAINS.clear();
        NEXT_ID.set(1);
    }

    private static int sourceId(Object block, Object biome, Object fullSource, Object level,
                                int x, int z, int y, int detailWidth) throws ReflectiveOperationException {
        Access a = Access.INSTANCE;
        SourceKey key = new SourceKey((String) a.blockSerial.invoke(block), (String) a.biomeSerial.invoke(biome));
        Integer existing = IDS.get(key);
        if (existing != null) return existing;
        synchronized (REGISTRY_LOCK) {
            existing = IDS.get(key);
            if (existing != null) return existing;
            int id = NEXT_ID.getAndIncrement();
            if (id <= 0 || id > MAX_SOURCES) return UNKNOWN;
            ReferenceData reference = a.referenceLuminance(block, biome, fullSource, level,
                    x, z, y, detailWidth);
            Object wrappedState = a.blockWrapped.invoke(block);
            boolean leaves = wrappedState instanceof BlockState state && state.is(BlockTags.LEAVES);
            FaceRecipe[] faceRecipes = wrappedState instanceof BlockState state
                    ? DhFaceRecipeResolver.resolve(state, reference.tintR, reference.tintG, reference.tintB)
                    : new FaceRecipe[6];
            SourceEntry entry = new SourceEntry(reference.referenceY,
                    reference.dhR, reference.dhG, reference.dhB, leaves,
                    wrappedState instanceof BlockState state ? state : null,
                    reference.tintR, reference.tintG, reference.tintB,
                    RtMaterialRegistry.INSTANCE.epoch(), faceRecipes);
            IDS.put(key, id);
            SOURCES.put(id, entry);
            return id;
        }
    }

    private static int[] quadIds(ProvenanceGrid grid, List<ByteBuffer> buffers) {
        int count = quadCount(buffers);
        int[] result = new int[count];
        byte[] quadBytes = new byte[64];
        int quad = 0;
        for (ByteBuffer source : buffers) {
            ByteBuffer copy = source.duplicate();
            int end = copy.position() + (copy.remaining() & ~63);
            while (copy.position() < end) {
                copy.get(quadBytes);
                result[quad++] = provenanceForQuad(grid, quadBytes, 0);
            }
        }
        return result;
    }

    /** Exact single- or multilayer Near face recipe, or {@code null} when it cannot be represented. */
    public static FaceRecipe faceRecipe(int provenanceId, int face) {
        if (provenanceId == UNKNOWN || face < 0 || face >= 6) return null;
        SourceEntry source = SOURCES.get(provenanceId);
        if (source == null) return null;
        long materialEpoch = RtMaterialRegistry.INSTANCE.epoch();
        if (source.state != null && materialEpoch != 0L && source.recipeEpoch != materialEpoch) {
            synchronized (REGISTRY_LOCK) {
                SourceEntry current = SOURCES.get(provenanceId);
                if (current != null && current.state != null && current.recipeEpoch != materialEpoch) {
                    FaceRecipe[] recipes = DhFaceRecipeResolver.resolve(current.state,
                            current.tintR, current.tintG, current.tintB);
                    current = new SourceEntry(current.referenceY, current.dhR, current.dhG, current.dhB,
                            current.leaves, current.state, current.tintR, current.tintG, current.tintB,
                            materialEpoch, recipes);
                    SOURCES.put(provenanceId, current);
                }
                source = current;
            }
        }
        return source == null ? null : source.faceRecipes[face];
    }

    private static int[] missingLifecycle(List<ByteBuffer> buffers) {
        int count = quadCount(buffers);
        return new int[count];
    }

    private static int quadCount(List<ByteBuffer> buffers) {
        long count = 0;
        for (ByteBuffer source : buffers) count += source.remaining() / 64;
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("Too many DH quads for provenance");
        return (int) count;
    }

    private static int provenanceForQuad(ProvenanceGrid grid, byte[] b, int q) {
        int face = b[q + 13] & 0xFF;
        if (face > 5) return UNKNOWN;
        int minX = 65535, minY = 65535, minZ = 65535;
        int maxX = 0, maxY = 0, maxZ = 0;
        for (int v = 0; v < 4; v++) {
            int p = q + v * 16;
            int x = u16(b, p), y = u16(b, p + 2), z = u16(b, p + 4);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        int x0 = minX, x1 = Math.max(minX, maxX - 1), xm = minX + Math.max(0, maxX - minX - 1) / 2;
        int y0 = minY, y1 = Math.max(minY, maxY - 1), ym = minY + Math.max(0, maxY - minY - 1) / 2;
        int z0 = minZ, z1 = Math.max(minZ, maxZ - 1), zm = minZ + Math.max(0, maxZ - minZ - 1) / 2;
        int[][] samples = switch (face) {
            case 0 -> new int[][]{{x0, minY, z0}, {x1, minY, z0}, {x0, minY, z1}, {x1, minY, z1}, {xm, minY, zm}};
            case 1 -> new int[][]{{x0, maxY - 1, z0}, {x1, maxY - 1, z0}, {x0, maxY - 1, z1}, {x1, maxY - 1, z1}, {xm, maxY - 1, zm}};
            case 2 -> new int[][]{{x0, y0, minZ}, {x1, y0, minZ}, {x0, y1, minZ}, {x1, y1, minZ}, {xm, ym, minZ}};
            case 3 -> new int[][]{{x0, y0, maxZ - 1}, {x1, y0, maxZ - 1}, {x0, y1, maxZ - 1}, {x1, y1, maxZ - 1}, {xm, ym, maxZ - 1}};
            case 4 -> new int[][]{{minX, y0, z0}, {minX, y0, z1}, {minX, y1, z0}, {minX, y1, z1}, {minX, ym, zm}};
            default -> new int[][]{{maxX - 1, y0, z0}, {maxX - 1, y0, z1}, {maxX - 1, y1, z0}, {maxX - 1, y1, z1}, {maxX - 1, ym, zm}};
        };
        int id = UNKNOWN;
        int[] leafCandidates = new int[samples.length];
        int leafCandidateCount = 0;
        boolean leafQuad = (b[q + 12] & 0xFF) == DH_MATERIAL_LEAVES;
        for (int[] sample : samples) {
            int candidate = grid.at(sample[0], sample[1], sample[2]);
            if (leafQuad) {
                SourceEntry source = SOURCES.get(candidate);
                if (source != null) {
                    boolean duplicate = false;
                    for (int i = 0; i < leafCandidateCount; i++) {
                        if (leafCandidates[i] == candidate) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) leafCandidates[leafCandidateCount++] = candidate;
                }
                continue;
            }
            if (candidate == OUTSIDE_GRID) return UNKNOWN;
            if (candidate == NO_RENDER_CELL || candidate == UNKNOWN) return UNKNOWN;
            if (id == UNKNOWN) id = candidate;
            else if (id != candidate) return UNKNOWN; // genuinely merged identities
        }
        if (!leafQuad) return id;
        int matchedLeaf = closestLeafIdentity(leafCandidates, leafCandidateCount,
                unshadeChannel(b[q + 8] & 0xFF, grid.faceShades[face]),
                unshadeChannel(b[q + 9] & 0xFF, grid.faceShades[face]),
                unshadeChannel(b[q + 10] & 0xFF, grid.faceShades[face]), true);
        return matchedLeaf != UNKNOWN ? matchedLeaf
                : closestLeafIdentity(leafCandidates, leafCandidateCount,
                unshadeChannel(b[q + 8] & 0xFF, grid.faceShades[face]),
                unshadeChannel(b[q + 9] & 0xFF, grid.faceShades[face]),
                unshadeChannel(b[q + 10] & 0xFF, grid.faceShades[face]), false);
    }

    /**
     * DH shades a merged leaf face from one representative source colour. Match its unshaded colour
     * against the exact leaf identities still present on the face, including luminance so nearby biome
     * variants with similar chroma cannot collapse onto whichever candidate was sampled first.
     * This preserves species and biome tint without falling back merely because another sample is air
     * or a neighbouring leaf type.
     */
    private static int closestLeafIdentity(int[] candidates, int count, int qr8, int qg8, int qb8,
                                           boolean requireLeafSource) {
        if (count == 0) return UNKNOWN;
        float qr = qr8 * (1.0f / 255.0f);
        float qg = qg8 * (1.0f / 255.0f);
        float qb = qb8 * (1.0f / 255.0f);
        int best = UNKNOWN;
        float bestError = Float.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            SourceEntry source = SOURCES.get(candidates[i]);
            if (source == null || (requireLeafSource && !source.leaves)) continue;
            float cr = linearToSrgb(source.dhR);
            float cg = linearToSrgb(source.dhG);
            float cb = linearToSrgb(source.dhB);
            float dr = qr - cr;
            float dg = qg - cg;
            float db = qb - cb;
            float error = dr * dr + dg * dg + db * db;
            if (error < bestError) {
                bestError = error;
                best = candidates[i];
            }
        }
        return best;
    }

    private static LinearRgb reconstructedDhRgb(int tileId, float lr, float lg, float lb) {
        byte[] tile = Access.INSTANCE.tilePixels(tileId);
        if (tile == null || tile.length < 1024) return new LinearRgb(lr, lg, lb);
        float sr = linearToSrgb(lr), sg = linearToSrgb(lg), sb = linearToSrgb(lb);
        double sumR = 0.0, sumG = 0.0, sumB = 0.0;
        int samples = 0;
        for (int i = 0; i < 256; i++) {
            int o = i * 4;
            float a = (tile[o + 3] & 255) / 255.0f;
            if (a <= 0.0f) continue;
            float r = lerp(sr, Math.min(1.0f, sr * ((tile[o] & 255) / 127.5f)), a);
            float g = lerp(sg, Math.min(1.0f, sg * ((tile[o + 1] & 255) / 127.5f)), a);
            float b = lerp(sb, Math.min(1.0f, sb * ((tile[o + 2] & 255) / 127.5f)), a);
            sumR += srgb(r);
            sumG += srgb(g);
            sumB += srgb(b);
            samples++;
        }
        return samples == 0 ? new LinearRgb(lr, lg, lb)
                : new LinearRgb((float) (sumR / samples), (float) (sumG / samples), (float) (sumB / samples));
    }

    private static float luminance(float r, float g, float b) {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float srgb(float c) { return c <= .04045f ? c / 12.92f : (float) Math.pow((c + .055f) / 1.055f, 2.4); }
    private static float linearToSrgb(float c) { return c <= .0031308f ? 12.92f * c : 1.055f * (float) Math.pow(c, 1.0 / 2.4) - .055f; }
    private static int unshadeChannel(int shaded, float shade) {
        if (shade >= 0.999f || shade <= 0.0f) return shaded;
        return Math.min(255, Math.max(0, Math.round(shaded / shade)));
    }
    private static int u16(byte[] b, int o) { return (b[o] & 255) | ((b[o + 1] & 255) << 8); }
    private static float[] makeSrgbLut() { float[] v = new float[256]; for (int i = 0; i < 256; i++) v[i] = srgb(i / 255.0f); return v; }

    static float gainForTest(float referenceY, float reconstructedY) {
        float value = reconstructedY > 1.0e-8f ? referenceY / reconstructedY : 1.0f;
        return Float.isFinite(value) && value > 0.0f ? value : 1.0f;
    }

    static int resolveQuadForTest(int detailWidth, int slices, int[] ids,
                                  short[] yMin, short[] yMax, byte[] quad) {
        return provenanceForQuad(new ProvenanceGrid(detailWidth, slices, ids, yMin, yMax), quad, 0);
    }

    private static void logFailureOnce(String phase, Throwable failure) {
        if (loggedFailure) return;
        loggedFailure = true;
        CausticaMod.LOGGER.warn("DH material provenance {} unavailable; affected FAR quads keep gain=1", phase, failure);
    }

    private record SourceKey(String block, String biome) { }
    public record BufferProvenance(int[] opaque, int[] transparent) { }
    public record FaceLayer(int materialId, float tintR, float tintG, float tintB,
                            float ua, float ub, float uc, float va, float vb, float vc) { }
    public record CutoutFill(float r, float g, float b) { }
    public record FaceRecipe(FaceLayer base, FaceLayer overlay, CutoutFill cutoutFill) {
        public FaceRecipe(FaceLayer base, FaceLayer overlay) {
            this(base, overlay, null);
        }
    }
    private record LinearRgb(float r, float g, float b) { }
    private record SourceEntry(float[] referenceY, float dhR, float dhG, float dhB,
                               boolean leaves, BlockState state, float tintR, float tintG, float tintB,
                               long recipeEpoch, FaceRecipe[] faceRecipes) { }
    private record ReferenceData(float[] referenceY, float dhR, float dhG, float dhB,
                                 float tintR, float tintG, float tintB) { }
    private record RenderGrid(ProvenanceGrid grid) { }
    private static final class PendingBuffers {
        private WeakReference<Object> opaqueList = new WeakReference<>(null);
        private WeakReference<Object> transparentList = new WeakReference<>(null);
        private int[] opaqueIds;
        private int[] transparentIds;

        void set(Object list, int[] ids, boolean transparent) {
            if (transparent) {
                transparentList = new WeakReference<>(list);
                transparentIds = ids;
            } else {
                opaqueList = new WeakReference<>(list);
                opaqueIds = ids;
            }
        }

        int[] ids(Object list, boolean transparent) {
            WeakReference<Object> expected = transparent ? transparentList : opaqueList;
            return expected.get() == list ? (transparent ? transparentIds : opaqueIds) : null;
        }
    }
    private static final class CaptureState {
        final ArrayList<RenderGrid> transformed = new ArrayList<>(5);
        ProvenanceGrid builderGrid;
        PendingBuffers pending;

        void dropTransformed() {
            transformed.clear();
        }
    }
    private record ProvenanceGrid(int detailWidth, int slices, int[] ids, short[] yMin, short[] yMax,
                                  float[] faceShades) {
        ProvenanceGrid(int detailWidth, int slices, int[] ids, short[] yMin, short[] yMax) {
            this(detailWidth, slices, ids, yMin, yMax, new float[]{0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f});
        }
        int at(int x, int y, int z) {
            int cx = Math.floorDiv(x, detailWidth), cz = Math.floorDiv(z, detailWidth);
            if (cx < 0 || cx >= COLUMN_WIDTH || cz < 0 || cz >= COLUMN_WIDTH) return OUTSIDE_GRID;
            int base = (cx * COLUMN_WIDTH + cz) * slices;
            for (int slice = 0; slice < slices; slice++) {
                int index = base + slice;
                int lo = yMin[index] & 0xFFFF, hi = yMax[index] & 0xFFFF;
                if (hi <= lo) break;
                if (y >= lo && y < hi) return ids[index];
            }
            return NO_RENDER_CELL;
        }
    }

    /** Reflection is intentionally confined to the exact installed DH 3.2.0-b integration ABI. */
    private static final class Access {
        static final Access INSTANCE = new Access();
        final Field fullMapping, renderPos, renderMaxSlices, blockColorCaches, cachedBaseColor;
        final Field facePixels, faceTinted, registryInstance, tilePixelsById;
        final Method fullDetail, fullColumn, renderPoint, renderExists, renderYMin, renderYMax;
        final Method fullId, fullBottom, fullHeight, listSize, listGet, blockForId, biomeForId;
        final Method blockAir, blockSerial, blockWrapped, biomeSerial, blockColor, minCornerX, minCornerZ, minHeight;
        final Method posSetX, posSetY, posSetZ, faceTexture, levelShade;
        final Object textureProvider;
        final Class<?> mutablePos;

        Access() {
            try {
                Class<?> full = Class.forName("com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2");
                fullMapping = full.getField("mapping");
                fullDetail = full.getMethod("getDataDetailLevel");
                fullColumn = full.getMethod("getColumnAtRelPos", int.class, int.class);
                Class<?> render = Class.forName("com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource");
                renderPos = render.getField("pos"); renderMaxSlices = render.getField("maxVerticalSliceCount");
                renderPoint = render.getMethod("getDataPoint", int.class, int.class, int.class);
                Class<?> renderUtil = Class.forName("com.seibel.distanthorizons.core.util.RenderDataPointUtil");
                renderExists = renderUtil.getMethod("doesDataPointExist", long.class);
                renderYMin = renderUtil.getMethod("getYMin", long.class); renderYMax = renderUtil.getMethod("getYMax", long.class);
                Class<?> fullUtil = Class.forName("com.seibel.distanthorizons.core.util.FullDataPointUtil");
                fullId = fullUtil.getMethod("getId", long.class); fullBottom = fullUtil.getMethod("getBottomY", long.class);
                fullHeight = fullUtil.getMethod("getHeight", long.class);
                Class<?> list = Class.forName("it.unimi.dsi.fastutil.longs.LongArrayList");
                listSize = list.getMethod("size"); listGet = list.getMethod("getLong", int.class);
                Class<?> mapping = Class.forName("com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap");
                blockForId = mapping.getMethod("getBlockStateWrapper", int.class); biomeForId = mapping.getMethod("getBiomeWrapper", int.class);
                Class<?> block = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper");
                blockAir = block.getMethod("isAir"); blockSerial = block.getMethod("getSerialString");
                blockWrapped = block.getMethod("getWrappedMcObject");
                Class<?> biome = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper");
                biomeSerial = biome.getMethod("getSerialString");
                Class<?> level = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper");
                mutablePos = Class.forName("com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable");
                Class<?> dhPos = Class.forName("com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos");
                blockColor = level.getMethod("getBlockColor", dhPos, biome, full, block);
                levelShade = level.getMethod("getShade", Class.forName("com.seibel.distanthorizons.core.enums.EDhDirection"));
                minHeight = level.getMethod("getMinHeight");
                posSetX = mutablePos.getMethod("setX", int.class); posSetY = mutablePos.getMethod("setY", int.class); posSetZ = mutablePos.getMethod("setZ", int.class);
                Class<?> section = Class.forName("com.seibel.distanthorizons.core.pos.DhSectionPos");
                minCornerX = section.getMethod("getMinCornerBlockX", long.class); minCornerZ = section.getMethod("getMinCornerBlockZ", long.class);
                Class<?> provider = Class.forName("com.seibel.distanthorizons.common.wrappers.block.BlockStateTextureProvider");
                textureProvider = provider.getField("INSTANCE").get(null);
                Class<?> direction = Class.forName("com.seibel.distanthorizons.core.enums.EDhDirection");
                faceTexture = provider.getMethod("getFaceTexture", block, direction);
                Class<?> face = Class.forName("com.seibel.distanthorizons.core.dataObjects.render.textures.BlockFaceTexture");
                facePixels = face.getField("argbPixels"); faceTinted = face.getField("tinted");
                Class<?> clientLevel = Class.forName("com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper");
                blockColorCaches = clientLevel.getDeclaredField("blockColorCacheByBlockState"); blockColorCaches.setAccessible(true);
                Class<?> colorCache = Class.forName("com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache");
                cachedBaseColor = colorCache.getDeclaredField("baseColor"); cachedBaseColor.setAccessible(true);
                Class<?> registry = Class.forName("com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry");
                registryInstance = registry.getField("INSTANCE");
                tilePixelsById = registry.getDeclaredField("tilePixelsById"); tilePixelsById.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported Distant Horizons material provenance ABI", e);
            }
        }

        long pointAt(Object column, int y) throws ReflectiveOperationException {
            if (column == null) return 0L;
            int count = ((Number) listSize.invoke(column)).intValue();
            for (int i = 0; i < count; i++) {
                long point = ((Number) listGet.invoke(column, i)).longValue();
                int bottom = ((Number) fullBottom.invoke(null, point)).intValue();
                int top = bottom + ((Number) fullHeight.invoke(null, point)).intValue();
                if (y >= bottom && y < top) return point;
            }
            return 0L;
        }

        ReferenceData referenceLuminance(Object block, Object biome, Object source, Object level,
                                         int x, int z, int y, int detailWidth) throws ReflectiveOperationException {
            Object p = mutablePos.getConstructor().newInstance();
            long pos = ((Number) source.getClass().getMethod("getPos").invoke(source)).longValue();
            int wx = ((Number) minCornerX.invoke(null, pos)).intValue() + x * detailWidth;
            int wz = ((Number) minCornerZ.invoke(null, pos)).intValue() + z * detailWidth;
            posSetX.invoke(p, wx); posSetY.invoke(p, y + ((Number) minHeight.invoke(level)).intValue()); posSetZ.invoke(p, wz);
            int finalColor = ((Number) blockColor.invoke(level, p, biome, source, block)).intValue();
            int baseColor = cachedBaseColor(level, blockWrapped.invoke(block), finalColor);
            float tintR = ratio(channel(finalColor, 16), channel(baseColor, 16));
            float tintG = ratio(channel(finalColor, 8), channel(baseColor, 8));
            float tintB = ratio(channel(finalColor, 0), channel(baseColor, 0));
            float[] result = new float[6];
            Object[] directions = (Object[]) Class.forName("com.seibel.distanthorizons.core.enums.EDhDirection").getField("ALL").get(null);
            for (Object direction : directions) {
                int faceIndex = direction.getClass().getField("faceIndex").getInt(direction);
                Object texture = faceTexture.invoke(textureProvider, block, direction);
                if (texture == null) continue;
                int[] pixels = (int[]) facePixels.get(texture);
                boolean tinted = faceTinted.getBoolean(texture);
                float[] mean = meanLinearRgb(pixels, tinted ? tintR : 1.0f,
                        tinted ? tintG : 1.0f, tinted ? tintB : 1.0f);
                result[faceIndex] = luminance(mean[0], mean[1], mean[2]);
            }
            return new ReferenceData(result,
                    SRGB_TO_LINEAR[channel(finalColor, 16)],
                    SRGB_TO_LINEAR[channel(finalColor, 8)],
                    SRGB_TO_LINEAR[channel(finalColor, 0)], tintR, tintG, tintB);
        }

        float[] faceShades(Object level) throws ReflectiveOperationException {
            float[] result = new float[6];
            Object[] directions = (Object[]) Class.forName(
                    "com.seibel.distanthorizons.core.enums.EDhDirection").getField("ALL").get(null);
            for (Object direction : directions) {
                int faceIndex = direction.getClass().getField("faceIndex").getInt(direction);
                if (faceIndex >= 0 && faceIndex < result.length) {
                    result[faceIndex] = ((Number) levelShade.invoke(level, direction)).floatValue();
                }
            }
            for (int i = 0; i < result.length; i++) if (!(result[i] > 0.0f)) result[i] = 1.0f;
            return result;
        }

        private int cachedBaseColor(Object level, Object block, int fallback) {
            try {
                @SuppressWarnings("unchecked") Map<Object, Object> caches = (Map<Object, Object>) blockColorCaches.get(level);
                Object cache = caches.get(block);
                return cache == null ? fallback : cachedBaseColor.getInt(cache);
            } catch (Throwable ignored) { return fallback; }
        }

        byte[] tilePixels(int tile) {
            try {
                Object registry = registryInstance.get(null);
                synchronized (registry) {
                    @SuppressWarnings("unchecked") List<byte[]> pixels = (List<byte[]>) tilePixelsById.get(registry);
                    return tile > 0 && tile < pixels.size() ? pixels.get(tile) : null;
                }
            } catch (Throwable ignored) { return null; }
        }

        private static float ratio(int value, int base) { return base > 0 ? Math.min(1.0f, value / (float) base) : 1.0f; }
        private static int channel(int argb, int shift) { return (argb >>> shift) & 255; }
        private static float[] meanLinearRgb(int[] pixels, float tr, float tg, float tb) {
            if (pixels == null || pixels.length == 0) return new float[3];
            double rSum = 0.0, gSum = 0.0, bSum = 0.0; int count = 0;
            for (int argb : pixels) {
                if (((argb >>> 24) & 255) == 0) continue;
                float r = SRGB_TO_LINEAR[Math.min(255, Math.round(((argb >>> 16) & 255) * tr))];
                float g = SRGB_TO_LINEAR[Math.min(255, Math.round(((argb >>> 8) & 255) * tg))];
                float b = SRGB_TO_LINEAR[Math.min(255, Math.round((argb & 255) * tb))];
                rSum += r; gSum += g; bSum += b; count++;
            }
            return count == 0 ? new float[3] : new float[]{(float) (rSum / count),
                    (float) (gSum / count), (float) (bSum / count)};
        }
    }
}
