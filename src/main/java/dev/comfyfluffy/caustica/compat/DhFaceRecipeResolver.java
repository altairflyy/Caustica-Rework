package dev.comfyfluffy.caustica.compat;

import com.mojang.blaze3d.platform.NativeImage;
import dev.comfyfluffy.caustica.mixin.SpriteContentsAccessor;
import dev.comfyfluffy.caustica.rt.material.RtMaterialRegistry;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Resolves a compact, immutable representation of the actual Near block-model face.
 *
 * <p>Only one or two complete, axis-aligned layers are representable by the FAR recipe shader.  A
 * complex/partial face is deliberately rejected and uses the established DH fallback path.  The
 * output owns only material ids, tints and UV coefficients; no model, sprite, world or DH object is
 * retained.</p>
 */
final class DhFaceRecipeResolver {
    private static final int FACE_COUNT = 6;
    private static final int MAX_LAYERS = 2;
    private static final float GEOMETRY_EPSILON = 1.0e-4f;
    private static final float UV_EPSILON = 2.0e-3f;
    /** Matches terrain any-hit and opacity-micromap coverage: alpha >= 0.5 is a visible texel. */
    private static final int CUTOUT_ALPHA_CUTOFF = 128;
    private static final ThreadLocal<Capture> CAPTURE = ThreadLocal.withInitial(Capture::new);

    private DhFaceRecipeResolver() {
    }

    static DhMaterialProvenance.FaceRecipe[] resolve(BlockState state,
                                                      float biomeTintR, float biomeTintG, float biomeTintB) {
        DhMaterialProvenance.FaceRecipe[] result = new DhMaterialProvenance.FaceRecipe[FACE_COUNT];
        if (state == null || !RtMaterialRegistry.INSTANCE.isReady()) return result;
        Capture capture = CAPTURE.get();
        try {
            Minecraft minecraft = Minecraft.getInstance();
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
            if (model == null) return result;
            capture.begin(state, biomeTintR, biomeTintG, biomeTintB,
                    RtMaterialRegistry.INSTANCE.requireSnapshot());
            capture.random.setSeed(state.getSeed(BlockPos.ZERO));
            model.emitQuads(capture.emitter, BlockAndTintGetter.EMPTY, BlockPos.ZERO, state,
                    capture.random, direction -> false);
            capture.finish(result);
        } catch (Throwable ignored) {
            // Resource reloads can briefly invalidate model/sprite state. UNKNOWN is the safe fallback path.
        } finally {
            capture.releaseReferences();
        }
        return result;
    }

    private static final class Capture {
        final ArrayList<LayerCandidate>[] faces;
        final QuadEmitter emitter;
        final RandomSource random = RandomSource.createThreadLocalInstance(0L);
        BlockState state;
        RtMaterialRegistry.Snapshot materials;
        float biomeTintR;
        float biomeTintG;
        float biomeTintB;
        boolean leafBlock;
        LayerCandidate leafRepresentative;

        @SuppressWarnings("unchecked")
        Capture() {
            faces = new ArrayList[FACE_COUNT];
            for (int i = 0; i < FACE_COUNT; i++) faces[i] = new ArrayList<>(MAX_LAYERS);
            emitter = Renderer.get().quadEmitter(this::accept);
        }

        void begin(BlockState state, float tr, float tg, float tb,
                   RtMaterialRegistry.Snapshot materials) {
            this.state = state;
            this.materials = materials;
            biomeTintR = tr;
            biomeTintG = tg;
            biomeTintB = tb;
            leafBlock = state.is(BlockTags.LEAVES);
            leafRepresentative = null;
            for (ArrayList<LayerCandidate> face : faces) face.clear();
        }

        void accept(MutableQuadView quad) {
            Direction direction = quad.lightFace();
            if (direction == null) return;
            int face = direction.get3DDataValue();
            if (face < 0 || face >= FACE_COUNT) return;
            ArrayList<LayerCandidate> layers = faces[face];
            if (leafBlock) {
                // DH ultimately represents foliage as a full voxel face. Preserve the exact emitted
                // Near sprite and tint even when a resource pack's decorative geometry is not itself
                // a complete axis-aligned cube face.
                LayerCandidate layer = layer(quad, direction);
                if (layer == null) layer = canonicalLeafLayer(quad);
                if (layer != null) {
                    if (leafRepresentative == null) leafRepresentative = layer;
                    if (layers.isEmpty()) layers.add(layer);
                }
                return;
            }
            if (layers.size() > MAX_LAYERS) return;
            LayerCandidate layer = layer(quad, direction);
            if (layer == null) {
                // A partial or non-planar member means the complete Near face cannot be represented.
                layers.clear();
                layers.add(LayerCandidate.INVALID);
                layers.add(LayerCandidate.INVALID);
                layers.add(LayerCandidate.INVALID);
                return;
            }
            layers.add(layer);
        }

        LayerCandidate layer(MutableQuadView quad, Direction direction) {
            TextureAtlasSprite sprite = sprite(quad);
            if (sprite == null) return null;
            float[] s = new float[4];
            float[] t = new float[4];
            float[] u = new float[4];
            float[] v = new float[4];
            float minS = Float.POSITIVE_INFINITY, maxS = Float.NEGATIVE_INFINITY;
            float minT = Float.POSITIVE_INFINITY, maxT = Float.NEGATIVE_INFINITY;
            float plane = Float.NaN;
            float uExtent = sprite.getU1() - sprite.getU0();
            float vExtent = sprite.getV1() - sprite.getV0();
            if (!(uExtent > 0.0f) || !(vExtent > 0.0f)) return null;
            for (int i = 0; i < 4; i++) {
                float x = quad.x(i), y = quad.y(i), z = quad.z(i);
                float p;
                switch (direction) {
                    case DOWN -> { s[i] = x;  t[i] = -z; p = y; }
                    case UP -> { s[i] = x;  t[i] = z; p = y; }
                    case NORTH -> { s[i] = -x; t[i] = -y; p = z; }
                    case SOUTH -> { s[i] = x;  t[i] = -y; p = z; }
                    case WEST -> { s[i] = z;  t[i] = -y; p = x; }
                    case EAST -> { s[i] = -z; t[i] = -y; p = x; }
                    default -> { return null; }
                }
                if (i == 0) plane = p;
                else if (Math.abs(p - plane) > GEOMETRY_EPSILON) return null;
                minS = Math.min(minS, s[i]); maxS = Math.max(maxS, s[i]);
                minT = Math.min(minT, t[i]); maxT = Math.max(maxT, t[i]);
                u[i] = (quad.u(i) - sprite.getU0()) / uExtent;
                v[i] = (quad.v(i) - sprite.getV0()) / vExtent;
            }
            float spanS = maxS - minS, spanT = maxT - minT;
            if (Math.abs(spanS - 1.0f) > GEOMETRY_EPSILON
                    || Math.abs(spanT - 1.0f) > GEOMETRY_EPSILON) return null;
            for (int i = 0; i < 4; i++) {
                s[i] = (s[i] - minS) / spanS;
                t[i] = (t[i] - minT) / spanT;
                if (!corner(s[i]) || !corner(t[i])) return null;
            }
            float[] uv = fitAffine(s, t, u, v);
            if (uv == null) return null;

            float tr = 0.0f, tg = 0.0f, tb = 0.0f;
            for (int i = 0; i < 4; i++) {
                int color = quad.color(i);
                tr += ((color >>> 16) & 255) * (1.0f / 1020.0f);
                tg += ((color >>> 8) & 255) * (1.0f / 1020.0f);
                tb += (color & 255) * (1.0f / 1020.0f);
            }
            boolean tinted = quad.tintIndex() >= 0;
            if (tinted) {
                tr *= biomeTintR;
                tg *= biomeTintG;
                tb *= biomeTintB;
            }
            int materialId = materials.resolve(sprite, state, false);
            ChunkSectionLayer chunkLayer = quad.chunkLayer();
            boolean overlay = tinted || chunkLayer != ChunkSectionLayer.SOLID;
            boolean solid = chunkLayer == ChunkSectionLayer.SOLID;
            DhMaterialProvenance.CutoutFill cutoutFill = !solid
                    && chunkLayer != ChunkSectionLayer.TRANSLUCENT
                    && state.is(BlockTags.LEAVES) ? coveredCutoutMean(sprite) : null;
            return new LayerCandidate(materialId, tr, tg, tb,
                    uv[0], uv[1], uv[2], uv[3], uv[4], uv[5], overlay, solid, cutoutFill, false);
        }

        LayerCandidate canonicalLeafLayer(MutableQuadView quad) {
            TextureAtlasSprite sprite = sprite(quad);
            if (sprite == null) return null;
            float tr = 0.0f, tg = 0.0f, tb = 0.0f;
            for (int i = 0; i < 4; i++) {
                int color = quad.color(i);
                tr += ((color >>> 16) & 255) * (1.0f / 1020.0f);
                tg += ((color >>> 8) & 255) * (1.0f / 1020.0f);
                tb += (color & 255) * (1.0f / 1020.0f);
            }
            if (quad.tintIndex() >= 0) {
                tr *= biomeTintR;
                tg *= biomeTintG;
                tb *= biomeTintB;
            }
            return new LayerCandidate(materials.resolve(sprite, state, false), tr, tg, tb,
                    1, 0, 0, 0, 1, 0, true, false, coveredCutoutMean(sprite), false);
        }

        TextureAtlasSprite sprite(MutableQuadView quad) {
            return Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(quad.atlas().getId()).spriteFinder().find(quad);
        }

        void finish(DhMaterialProvenance.FaceRecipe[] result) {
            for (int face = 0; face < FACE_COUNT; face++) {
                if (leafBlock) {
                    LayerCandidate layer = faces[face].isEmpty() ? leafRepresentative : faces[face].get(0);
                    result[face] = layer == null ? null
                            : new DhMaterialProvenance.FaceRecipe(layer.toLayer(), null, layer.cutoutFill);
                } else {
                    result[face] = assemble(faces[face]);
                }
            }
        }

        void releaseReferences() {
            state = null;
            materials = null;
            leafRepresentative = null;
        }
    }

    private static boolean corner(float value) {
        return Math.abs(value) <= GEOMETRY_EPSILON || Math.abs(value - 1.0f) <= GEOMETRY_EPSILON;
    }

    /** Fit u=a*s+b*t+c and v=d*s+e*t+f, then require the fourth point to agree. */
    private static float[] fitAffine(float[] s, float[] t, float[] u, float[] v) {
        for (int a = 0; a < 2; a++) {
            for (int b = a + 1; b < 3; b++) {
                for (int c = b + 1; c < 4; c++) {
                    float det = determinant(s[a], t[a], s[b], t[b], s[c], t[c]);
                    if (Math.abs(det) <= 1.0e-6f) continue;
                    float[] uc = solve(s, t, u, a, b, c, det);
                    float[] vc = solve(s, t, v, a, b, c, det);
                    boolean valid = true;
                    for (int i = 0; i < 4; i++) {
                        float pu = uc[0] * s[i] + uc[1] * t[i] + uc[2];
                        float pv = vc[0] * s[i] + vc[1] * t[i] + vc[2];
                        if (Math.abs(pu - u[i]) > UV_EPSILON || Math.abs(pv - v[i]) > UV_EPSILON) {
                            valid = false;
                            break;
                        }
                    }
                    if (valid) return new float[]{uc[0], uc[1], uc[2], vc[0], vc[1], vc[2]};
                }
            }
        }
        return null;
    }

    static float[] fitAffineForTest(float[] s, float[] t, float[] u, float[] v) {
        return fitAffine(s, t, u, v);
    }

    /** Pure structural classifier shared by runtime capture and resolver unit tests. */
    private static DhMaterialProvenance.FaceRecipe assemble(ArrayList<LayerCandidate> layers) {
        if (layers.isEmpty() || layers.size() > MAX_LAYERS
                || layers.stream().anyMatch(layer -> layer.invalid)) return null;
        layers.sort(Comparator.comparing(layer -> layer.overlay));
        LayerCandidate first = layers.get(0);
        if (layers.size() == 1 && first.cutoutFill != null) {
            return new DhMaterialProvenance.FaceRecipe(first.toLayer(), null, first.cutoutFill);
        }
        if (!first.solid) return null; // only tagged foliage may preserve CUTOUT colour on solid FAR geometry
        LayerCandidate second = layers.size() == 2 ? layers.get(1) : null;
        return new DhMaterialProvenance.FaceRecipe(first.toLayer(),
                second == null ? null : second.toLayer());
    }

    /** Linear RGB mean of texels that the canonical terrain alpha test accepts. */
    private static DhMaterialProvenance.CutoutFill coveredCutoutMean(TextureAtlasSprite sprite) {
        var contents = sprite.contents();
        int width = contents.width();
        int height = contents.height();
        NativeImage image = ((SpriteContentsAccessor) contents).caustica$originalImage();
        if (image == null || width <= 0 || height <= 0) return null;
        int frameRowSize = Math.max(1, image.getWidth() / width);
        var frames = contents.getUniqueFrames();
        int frameCount = contents.isAnimated() ? frames.size() : 1;
        double r = 0.0, g = 0.0, b = 0.0;
        long covered = 0L;
        for (int f = 0; f < frameCount; f++) {
            int frame = contents.isAnimated() ? frames.getInt(f) : 0;
            int frameX = (frame % frameRowSize) * width;
            int frameY = (frame / frameRowSize) * height;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = image.getPixel(frameX + x, frameY + y);
                    if (ARGB.alpha(pixel) < CUTOUT_ALPHA_CUTOFF) continue;
                    r += srgbToLinear(ARGB.red(pixel));
                    g += srgbToLinear(ARGB.green(pixel));
                    b += srgbToLinear(ARGB.blue(pixel));
                    covered++;
                }
            }
        }
        if (covered == 0L) return null;
        float inv = 1.0f / covered;
        return new DhMaterialProvenance.CutoutFill((float) r * inv, (float) g * inv, (float) b * inv);
    }

    private static float srgbToLinear(int channel) {
        float c = channel * (1.0f / 255.0f);
        return c <= 0.04045f ? c / 12.92f
                : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    static DhMaterialProvenance.FaceRecipe assembleForTest(LayerCandidate... layers) {
        return assemble(new ArrayList<>(java.util.List.of(layers)));
    }

    private static float determinant(float s0, float t0, float s1, float t1, float s2, float t2) {
        return s0 * (t1 - t2) - t0 * (s1 - s2) + s1 * t2 - t1 * s2;
    }

    private static float[] solve(float[] s, float[] t, float[] value,
                                 int i0, int i1, int i2, float det) {
        float a = (value[i0] * (t[i1] - t[i2])
                - t[i0] * (value[i1] - value[i2])
                + value[i1] * t[i2] - t[i1] * value[i2]) / det;
        float b = (s[i0] * (value[i1] - value[i2])
                - value[i0] * (s[i1] - s[i2])
                + s[i1] * value[i2] - value[i1] * s[i2]) / det;
        float c = (s[i0] * (t[i1] * value[i2] - value[i1] * t[i2])
                - t[i0] * (s[i1] * value[i2] - value[i1] * s[i2])
                + value[i0] * (s[i1] * t[i2] - t[i1] * s[i2])) / det;
        return new float[]{a, b, c};
    }

    record LayerCandidate(int materialId, float tintR, float tintG, float tintB,
                          float ua, float ub, float uc, float va, float vb, float vc,
                          boolean overlay, boolean solid, DhMaterialProvenance.CutoutFill cutoutFill,
                          boolean invalid) {
        static final LayerCandidate INVALID = new LayerCandidate(0, 1, 1, 1,
                1, 0, 0, 0, 1, 0, false, false, null, true);

        DhMaterialProvenance.FaceLayer toLayer() {
            return new DhMaterialProvenance.FaceLayer(materialId, tintR, tintG, tintB,
                    ua, ub, uc, va, vb, vc);
        }
    }
}
