package dev.comfyfluffy.caustica.rt;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.client.CausticaJitter;
import dev.comfyfluffy.caustica.mixin.CommandEncoderAccessor;
import dev.comfyfluffy.caustica.rt.gen.RestirReservoirData;
import dev.comfyfluffy.caustica.rt.gen.WorldPushConstantsData;
import dev.comfyfluffy.caustica.rt.terrain.RtLodTerrain;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.BreakEntry;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float2;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float3;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float4;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Int4;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import dev.comfyfluffy.caustica.rt.accel.RtBuffer;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.entity.RtEntities;
import dev.comfyfluffy.caustica.rt.environment.CloudModule;
import dev.comfyfluffy.caustica.rt.environment.EnvironmentParameters;
import dev.comfyfluffy.caustica.rt.environment.FogModule;
import dev.comfyfluffy.caustica.rt.entity.RtEntityTextures;
import dev.comfyfluffy.caustica.rt.material.RtMaterialRegistry;
import dev.comfyfluffy.caustica.rt.post.PostProcessing;
import dev.comfyfluffy.caustica.rt.framegen.FrameGenerationResources;
import dev.comfyfluffy.caustica.rt.overlay.RtWorldOverlay;
import dev.comfyfluffy.caustica.rt.terrain.RtTerrain;
import dev.comfyfluffy.caustica.rt.trace.WorldTraceResources;
import dev.comfyfluffy.caustica.rt.trace.TraceFrameResources;
import dev.comfyfluffy.caustica.rt.scene.TerrainSceneContribution;
import dev.comfyfluffy.caustica.rt.scene.LodSceneContribution;
import dev.comfyfluffy.caustica.rt.scene.RtScene;
import dev.comfyfluffy.caustica.rt.scene.SceneAssembler;
import dev.comfyfluffy.caustica.rt.lighting.RestirSystem;
import dev.comfyfluffy.caustica.rt.lighting.SharcRadianceCache;
import dev.comfyfluffy.caustica.rt.reconstruction.SvgfReconstructionBackend;
import dev.comfyfluffy.caustica.rt.reconstruction.DlssRrReconstructionBackend;
import dev.comfyfluffy.caustica.rt.reconstruction.experimental.nrd.ExperimentalNrdBackend;
import dev.comfyfluffy.caustica.rt.upscale.FsrUpscalerBackend;
import dev.comfyfluffy.caustica.rt.upscale.XessUpscalerBackend;
import dev.comfyfluffy.caustica.rt.upscale.NativeUpscalerBackend;
import dev.comfyfluffy.caustica.rt.upscale.UpscalerRuntime;
import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import dev.comfyfluffy.caustica.rt.frame.FramePipeline;
import dev.comfyfluffy.caustica.rt.graph.FrameGraph;
import dev.comfyfluffy.caustica.rt.graph.GraphExecution;
import dev.comfyfluffy.caustica.rt.graph.DenoiserBarrierPlan;
import dev.comfyfluffy.caustica.rt.graph.DenoiserBarriers;
import dev.comfyfluffy.caustica.rt.frame.FrameCursor;
import dev.comfyfluffy.caustica.rt.frame.PathTracePass;
import dev.comfyfluffy.caustica.rt.frame.PostPresentPass;
import dev.comfyfluffy.caustica.rt.frame.PrepareFramePass;
import dev.comfyfluffy.caustica.rt.frame.ReconstructionPass;
import dev.comfyfluffy.caustica.rt.frame.TemporalResetReason;
import dev.comfyfluffy.caustica.rt.frame.TemporalState;
import dev.comfyfluffy.caustica.rt.frame.UpscalePass;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * On-screen composite. Each frame, ray-trace into a render-res storage image (+ guide buffers), use
 * DLSS Ray Reconstruction to denoise and upscale it to display res, write that into a storage-capable
 * copy of the world color, and copy the result back to the world target at the
 * end-of-world seam. Gated by {@code -Dcaustica.rt=true}.
 *
 * <p>The path tracer and its guide buffers run at the configured render scale of display res with a per-frame
 * sub-pixel camera jitter; the DLSS-RR backend reconstructs the display-res image. With RR
 * disabled the trace runs at 1:1 and a linear blit stands in for the upscale (a raw, noisy reference).
 *
 * <p>Traces the extracted {@link RtTerrain} with perspective camera rays (camera matrices captured
 * each frame via {@link #captureFrame}); writes nothing until terrain is available.
 * Pipelines/SBT/descriptors are built once; sized images rebuilt on resize.
 */
public final class RtComposite {
    public static final RtComposite INSTANCE = new RtComposite();

    public static boolean enabled() {
        return CausticaConfig.Rt.ENABLED.value();
    }

    // WorldPushData and its serializer are generated from Slang's reflected Std430DataLayout. Java never
    // owns or calculates a shader byte offset, struct size, array stride, or fixed-array capacity.
    private static final int WORLD_PUSH_SIZE = WorldPushData.BYTE_SIZE;
    // Per-frame DH/Voxy hand-off readiness mask, appended in the same BDA ring slot behind WorldPush.
    private static final int READY_MASK_OFFSET = (WORLD_PUSH_SIZE + 15) & ~15;
    // Covers a 257x257x48-section window (render distance 128) with room to spare.
    private static final int READY_MASK_CAPACITY = 512 * 1024;
    // Vanilla's authored classic cloud shape (CloudModule): a bit-packed clouds.png occupancy bitmap
    // riding the same ring slot, addressed from WorldPush.cloudCellsAddr exactly like the ready mask
    // above is addressed from the push constants — no extra binding, one flush covers all three.
    private static final int CLOUD_CELLS_OFFSET = (READY_MASK_OFFSET + READY_MASK_CAPACITY + 15) & ~15;
    private static final int WORLD_PUSH_BUFFER_SIZE = CLOUD_CELLS_OFFSET + CloudModule.MAP_BYTES;
    // Real inline push constants (fast constant-bank reads), separate from the WorldPush BDA ring above.
    // Hot addresses/frameIndex and raygen's debugView avoid unnecessary global-memory dereferences;
    // WorldPushConstantsData is generated from the same Slang module and owns this second ABI as well.
    // RR guide buffers (bindings 3..8) + NRD signals (bindings 9..11: viewZ + per-lobe radiance/hit
    // distance). The NRD images are only written when FEATURE_NRD is on, but the bindings always exist.
    private static final int GUIDE_COUNT = 9;
    private static final long PATH_RECORD_BYTES = 48L;
    // Reflected from PackedRestirReservoir's std430 array stride (world_layout_probe.slang).
    private static final long RESTIR_RECORD_BYTES = RestirReservoirData.BYTE_SIZE;
    private static int debugView() {
        return CausticaConfig.Rt.Composite.DEBUG_VIEW.value();
    }

    private static int spp() {
        return CausticaConfig.Rt.Composite.SPP.value();
    }

    private static int maxBounces() {
        return CausticaConfig.Rt.Composite.MAX_BOUNCES.value();
    }

    private static boolean waterWaves() {
        return CausticaConfig.Rt.Composite.WATER_WAVES.value();
    }

    /**
     * Shader-only POM parameters: x relief depth (blocks), y max texel crossings, z unused,
     * w fade distance.
     *
     * <p>The shader walks the LabPBR height field as a grid of per-texel box columns (the same
     * Amanatides &amp; Woo walk the classic cloud deck uses), so its cost is bounded by texel crossings
     * instead of by a fixed layer count. Deeper relief slants the walk further across the sprite before
     * it reaches the base plane, so the budget scales with the configured depth; the shader clamps it
     * to PARALLAX_MIN/MAX_CROSSINGS either way.
     */
    private static Float4 parallaxParams() {
        boolean enabled = CausticaConfig.Rt.Composite.PARALLAX_ENABLED.value();
        float strength = CausticaConfig.Rt.Composite.PARALLAX_STRENGTH.value();
        float depth = enabled ? strength * 0.125f : 0.0f;
        // POM Quality slider: multiplies the strength-derived texel-crossing budget without touching
        // the relief depth, so the player trades sampling level for fps independently of the look.
        // The outer clamp keeps the pushed budget inside the shader's compiled bounds (the shader
        // re-clamps it too — see PARALLAX_MIN/MAX_CROSSINGS in world_common.slang).
        float quality = CausticaConfig.Rt.Composite.PARALLAX_QUALITY.value();
        float crossings = Math.min(128.0f,
                Math.max(16.0f, Math.round(32.0f * Math.max(1.0f, strength) * quality)));
        return new Float4(depth, crossings, 0.0f,
                CausticaConfig.Rt.Composite.PARALLAX_DISTANCE.value());
    }

    // ---- Shader feature flags (WorldPush.featureFlags). Mirrors world_common.slang's FEATURE_*
    // constants: these are player-facing effect toggles, kept in their own word so they can never be
    // confused with WorldPush.flags, which describes the camera's physical state for the frame.
    private static final int FEATURE_SSS = 1;
    private static final int FEATURE_WEATHER_LIGHTING = 2;
    private static final int FEATURE_DENOISER = 4;
    private static final int FEATURE_CLOUDS = 8;
    private static final int FEATURE_CLOUDS_VOLUMETRIC = 16;
    private static final int FEATURE_RESTIR = 32;
    // Per-lobe NRD signal capture: only the NRD path needs it (it costs an extra shadow ray for the
    // lobe split), so it is a feature bit rather than something the tracer always pays for.
    private static final int FEATURE_NRD = 64;
    // frameViews().viewZ() capture. Every denoised non-DLSS path needs it (SVGF's reprojection validation and
    // sky cutoff, NRD's IN_VIEWZ), so it is set for both denoisers.
    private static final int FEATURE_VIEWZ = 128;
    private static final int FEATURE_SHARC = 256;

    // ---- Dimension ids (WorldPush.dimension). Mirrors world_common.slang's DIMENSION_* constants.
    // The Overworld runs the atmosphere march and the sun/moon cycle; the Nether and the End have no
    // celestial cycle at all and draw their own skybox in world.rmiss.
    private static final int DIMENSION_OVERWORLD = 0;
    private static final int DIMENSION_NETHER = 1;
    private static final int DIMENSION_END = 2;

    /**
     * Collect this frame's shader feature toggles into the {@code featureFlags} word. Read fresh every
     * frame (never cached) so the Video Settings toggles take effect on the next frame, the way every
     * other runtime-tunable option in the renderer does.
     */
    private int featureFlags() {
        int flags = 0;
        if (CausticaConfig.Rt.Composite.SSS.value()) {
            flags |= FEATURE_SSS;
        }
        if (CausticaConfig.Rt.Composite.WEATHER_LIGHTING.value()) {
            flags |= FEATURE_WEATHER_LIGHTING;
        }
        if (restirSystem.featureEnabled()) {
            flags |= FEATURE_RESTIR;
        }
        if (sharc.featureEnabled()) {
            flags |= FEATURE_SHARC;
        }
        if (cloudModule.enabled()) {
            flags |= FEATURE_CLOUDS;
            // The style is a feature bit, not a packed float lane: featureFlags is exactly the word for
            // player-facing effect toggles, and an integer bit survives every float quirk.
            if (cloudModule.volumetric()) {
                flags |= FEATURE_CLOUDS_VOLUMETRIC;
            }
        }
        // Reports what the pipeline is ACTUALLY doing, not just what the option asks for:
        // Backend availability already folds in the backend switch, and a debug view suppresses RR
        // entirely (see recordFrame's rrPath), so a shader reading this flag learns whether its output
        // will be denoised rather than whether the player would like it to be.
        if (dlssRrBackend.available() && debugView() == 0) {
            flags |= FEATURE_DENOISER;
        } else {
            if (nrdBackend.selected() && debugView() == 0) {
                // Per-lobe signal capture only runs when NRD is the active denoiser: RR denoises
                // internally and SVGF works on the combined radiance, so capturing the split would
                // burn an extra shadow ray plus bandwidth for buffers nothing reads.
                flags |= FEATURE_NRD;
                flags |= FEATURE_VIEWZ;
            } else if (CausticaConfig.Rt.Denoise.ENABLED.value() && debugView() == 0) {
                // SVGF needs frameViews().viewZ() for its geometry-validated reprojection and its sky cutoff.
                flags |= FEATURE_VIEWZ;
            }
        }
        return flags;
    }

    /**
     * Keep the SHaRC cache buffer matched to the current config, and honour an explicit "reset" action
     * from the options UI. Called every frame before the trace so a live toggle takes effect on the
     * next frame; the shader feature flag is keyed on {@link SharcRadianceCache#entryCount()} being non-zero so an
     * enabled toggle with no buffer (or a failed allocation) degrades to the normal tracer.
     */
    private void syncSharcResources(RtContext ctx) {
        ClientLevel level = Minecraft.getInstance().level;
        sharc.sync(ctx, level, level == null ? DIMENSION_OVERWORLD : dimensionId(level), frameCounter);
    }

    // Finite sun/moon angular sizes let NEE shadow rays sample the light disk (soft, contact-hardening
    // penumbrae). Radii in degrees; the real sun/moon are ~0.27°, but a touch larger reads pleasantly.
    private static final int WATER_ANCHOR_MASK = 4095;

    // Matches the viewZ cap the tracer writes for sky/miss pixels: everything beyond is passed
    // through the denoise chain raw (the sky never accumulates history).
    // Celestial rotation axis (the pole the sun/moon arc about): perpendicular to the east-west arc,
    // tilted by SUN_NOON_SOUTH_TILT. Pushed so the sky shader can build the sun/moon square's tangent
    // frame (right = travel direction) and wheel the starfield. = normalize(noonDir x sunriseDir).
    // Sign of the sub-pixel jitter as reported to DLSS-RR + applied to the primary ray, mirroring the
    // validated DLSS-SR convention (Vulkan flipped clip space wants Y negated).
    private static float jitterSignX() {
        return CausticaConfig.Rt.Composite.JITTER_SIGN_X.value();
    }

    private static float jitterSignY() {
        return CausticaConfig.Rt.Composite.JITTER_SIGN_Y.value();
    }

    private static float sunNoonTilt() {
        return CausticaConfig.Rt.Composite.SUN_NOON_SOUTH_TILT.value();
    }

    private static float sunNoonY() {
        return Mth.cos(sunNoonTilt());
    }

    private static float sunNoonZ() {
        return Mth.sin(sunNoonTilt());
    }

    private static float celestialAxisY() {
        return -sunNoonZ();
    }

    private static float celestialAxisZ() {
        return sunNoonY();
    }

    // Monotonic per-composite frame counter used for cache eviction, shader sampling, and diagnostics.
    private static volatile long frameCounter;

    public static long frameCounter() {
        return frameCounter;
    }

    private final WorldTraceResources worldTraceResources =
            new WorldTraceResources(WORLD_PUSH_BUFFER_SIZE, GUIDE_COUNT);
    private final TraceFrameResources traceFrameResources = new TraceFrameResources(PATH_RECORD_BYTES);
    private final PostProcessing postProcessing = new PostProcessing(
            destruction -> RtContext.get().frameTailRetirement().retire(destruction));
    private final FrameGenerationResources frameGenerationResources = new FrameGenerationResources(
            destruction -> RtContext.get().frameTailRetirement().retire(destruction));
    // ReSTIR DI/GI history is a strict two-buffer ping-pong: a dispatch reads only `previous` and writes
    // only `current`, so spatial neighbour reuse never races another raygen invocation. The pair exists
    // only while the player setting is ON; live toggles idle the device before destruction/allocation.
    private final RestirSystem restirSystem = new RestirSystem();
    // ---- SVGF (the renderer's own denoiser for every non-DLSS path).
    //
    // colour/history ping-pong (rgb = colour, a = accumulated frame count), the luminance-moment
    // ping-pong feeding the variance estimate, and the à-trous ping-pong (rgb = colour,
    // a = variance). The reprojection also needs LAST frame's geometry to validate history against,
    // which is what the prev-guide copies hold.
    private final SvgfReconstructionBackend svgfBackend = new SvgfReconstructionBackend();
    private final DlssRrReconstructionBackend dlssRrBackend = new DlssRrReconstructionBackend();
    private final ExperimentalNrdBackend nrdBackend = new ExperimentalNrdBackend();
    private final UpscalerRuntime upscalers = UpscalerRuntime.INSTANCE;
    // Experimental SHaRC (Spatially Hashed Radiance Cache). Shader-only — the host only owns the
    // persistent cache buffer and publishes its device address (no native lib, no extra binding).
    private final SharcRadianceCache sharc = SharcRadianceCache.INSTANCE;
    private final CloudModule cloudModule = CloudModule.INSTANCE;
    private final FogModule fogModule = FogModule.INSTANCE;


    // Motion-vector reprojection state: the previous frame's camera-relative view-projection and
    // camera position, read into the push constant each frame then advanced at frame end.
    private final Matrix4f mvPrevProjView = new Matrix4f();
    private final Matrix4f mvCurProjView = new Matrix4f();
    private final Matrix4f mvPushMatrix = new Matrix4f();
    private final Matrix4f frameInvViewProj = new Matrix4f();
    private final BlockPos.MutableBlockPos cameraBlockPos = new BlockPos.MutableBlockPos();
    private double mvPrevCamX;
    private double mvPrevCamY;
    private double mvPrevCamZ;
    private float mvCamDeltaX;
    private float mvCamDeltaY;
    private float mvCamDeltaZ;
    private boolean mvHasPrev;
    private float previousWaterWaveTime;
    private boolean waterWaveTimeValid;
    private boolean failed;
    private boolean loggedActive;

    // Camera captured each frame from GameRenderer (unjittered level projection + camera rotation + pos).
    private final Matrix4f frameProjection = new Matrix4f();
    private final Matrix4f frameViewRotation = new Matrix4f();
    private double camX;
    private double camY;
    private double camZ;
    private boolean frameCaptured;
    private FrameContext frameContext;
    private final TemporalState temporalState = new TemporalState();
    private final PrepareFramePass prepareFramePass = new PrepareFramePass(this::prepareFrame);
    private final PathTracePass pathTracePass = new PathTracePass(this::recordPathTrace);
    private final ReconstructionPass reconstructionPass = new ReconstructionPass(this::reconstructFrame);
    private final UpscalePass upscalePass = new UpscalePass(this::upscaleFrame);
    private final PostPresentPass postPresentPass = new PostPresentPass(this::postPresentFrame);
    private final FramePipeline framePipeline = new FramePipeline(
            prepareFramePass, pathTracePass, reconstructionPass, upscalePass, postPresentPass);
    private final FrameGraph frameGraph = FrameGraph.shadow(framePipeline);
    private final GraphExecution graphExecution = new GraphExecution(frameGraph, framePipeline);
    private FrameCursor pipelineCursor;
    private int loggedDenoiserBarrierMode = -1;
    private String loggedUpscalerBarrierMode;
    private int loggedPathTraceBarrierMode = -1;
    private RtContext pipelineContext;
    private FrameInputs pipelineInputs;
    private VkCommandBuffer pipelineCommand;
    private MemoryStack pipelineStack;
    private ByteBuffer pipelinePushConstants;
    private ReconstructionInput pipelineReconstructionInput;
    private ReconstructionResult pipelineReconstructionResult;
    private UpscaleInput pipelineUpscaleInput;
    private long pipelinePostPresentTarget;
    private double previousFrameCamX;
    private double previousFrameCamY;
    private double previousFrameCamZ;

    // This frame's TLAS handle, published after prepareTlas so the world-overlay pass (block outline's
    // rayQueryEXT occlusion test) can bind the exact same acceleration structure the primary trace used —
    // same-queue submission order (RtWorldOverlay's transient buffer runs later, same graphics queue)
    // makes the TLAS build's writes visible without an extra semaphore, matching every other overlay
    // feature's reliance on in-order queue execution for this frame's world content.
    private volatile long currentTlasHandle;
    private RtGpuExecutor.GraphicsUse pendingGraphicsUse;

    private RtComposite() {
    }

    /** This frame's TLAS handle (0 if none built yet), for {@code dev.comfyfluffy.caustica.rt.overlay} occlusion queries. */
    public long currentTlasHandle() {
        return currentTlasHandle;
    }

    public boolean hasFailed() {
        return this.failed;
    }

    /**
     * Whether the current frame must retain vanilla world rendering while RT resource state converges.
     *
     * <p>The composite still runs at the normal seam so it can consume the one-frame epoch gate or observe
     * the newly uploaded atlas. This method only prevents {@code LevelRenderer} from being cancelled before
     * a deliberately transient {@link #composite} return. Such a return is not a renderer failure and must
     * not trip {@code VanillaRenderController}'s permanent safety latch.</p>
     */
    public boolean requiresVanillaWorldFallback() {
        return worldTraceResources.requiresVanillaFallback();
    }

    /**
     * Clear the failure latch on an explicit render-state invalidation (F3+A, dimension change) so RT
     * re-arms after a transient error instead of staying on vanilla until restart. A deterministic
     * failure just latches again on the next frame (bounded log spam: one error line per invalidation).
     */
    public void resetFailureLatch() {
        recordTemporalReset(TemporalResetReason.MANUAL);
        broadcastTemporalReset(() -> {
            if (failed) {
                failed = false;
                CausticaMod.LOGGER.info("RT failure latch cleared by render-state invalidation; retrying RT");
            }
        });
    }

    /** Collects a legacy reset cause in the single AER-013 coordinator. */
    public void recordTemporalReset(TemporalResetReason reason) {
        temporalState.collect(reason);
        CausticaMod.LOGGER.debug("RT temporal reset reason: {}", reason);
    }

    /**
     * Delivers the collected request to the legacy recipient at the original call-site. The
     * coordinator clears its reason bitset only after the recipient returns successfully.
     */
    private void broadcastTemporalReset(Runnable legacyDelivery) {
        if (frameContext == null) {
            // Resource invalidation can happen before the first captured frame. Preserve the
            // legacy timing in that case and acknowledge only after the direct delivery succeeds.
            legacyDelivery.run();
            temporalState.acknowledgeLegacyDelivery();
            return;
        }
        temporalState.snapshot(frameContext);
        temporalState.broadcast(request -> legacyDelivery.run());
    }

    /** Capture the frame's camera for the next composite. Called from GameRendererMixin. */
    public void captureFrame(Matrix4f projection, Matrix4fc viewRotation, double cameraX, double cameraY, double cameraZ) {
        frameProjection.set(projection);
        frameViewRotation.set(viewRotation);
        camX = cameraX;
        camY = cameraY;
        camZ = cameraZ;
        frameCaptured = true;
    }

    /**
     * The frame's forward camera-relative view-projection (jitter-free), exactly what {@code world.rgen}
     * traced with — overlay raster passes ({@code dev.comfyfluffy.caustica.rt.overlay}) reuse it so their content lands
     * pixel-exact on the RT image. Valid after {@code updateMotion} ran this frame; do not mutate.
     */
    public Matrix4fc currentViewProjection() {
        return mvCurProjView;
    }

    /**
     * Reset per-frame present state at the very start of {@link net.minecraft.client.renderer.GameRenderer}
     * render (before any RT work). Critical for menu/no-world frames: {@link #composite()} is only called
     * while a level is rendering ({@code WorldRenderScaler} opens its window in {@code renderLevel}), so on
     * menu frames {@code composite} never runs and {@code hdrWrittenThisFrame} would otherwise keep its stale
     * {@code true} from the last world frame — presenting a black/stale HDR image behind the menu. Clearing it
     * here every frame makes {@link #isHdrPresentActive()} false on menu frames so the SDR convert-present path
     * runs instead.
     */
    public void beginFrame() {
        if (pendingGraphicsUse != null) {
            throw new IllegalStateException("Previous RT graphics use was never completed");
        }
        RtFrameStats.FRAME.beginIfInactive();
        RtContext ctx = RtContext.currentOrNull();
        if (ctx != null) {
            ctx.accelerationStructures().recordDiagnostics(RtFrameStats.FRAME);
        }
        postProcessing.beginFrame();
    }

    /** This frame's completion token, valid until {@link #finishGraphicsUse()} signals it. */
    public RtGpuExecutor.GraphicsUse currentGraphicsUse() {
        RenderSystem.assertOnRenderThread();
        return pendingGraphicsUse;
    }

    /** Signal this RT frame's shared completion token after its final TLAS consumer (world overlay). */
    public void finishGraphicsUse() {
        RtGpuExecutor.GraphicsUse graphicsUse = pendingGraphicsUse;
        if (graphicsUse == null) {
            return;
        }
        RtContext ctx = RtContext.currentOrNull();
        if (ctx == null) {
            throw new IllegalStateException("RT context disappeared before graphics use completed");
        }
        var encoder = (VulkanCommandEncoder) ((CommandEncoderAccessor) RenderSystem.getDevice()
                .createCommandEncoder()).caustica$getBackend();
        ctx.gpuExecutor().endGraphicsUse(encoder, graphicsUse);
        pendingGraphicsUse = null;
    }

    public void endFrame() {
        RtFrameStats.FRAME.end();
    }

    public boolean composite(GpuTexture nativeColor, int width, int height) {
        frameCounter++; // global frame serial used by remaining per-frame/entity rings and diagnostics
        VulkanDiagnostics.setInFlight("graphics-latest", "frame=" + frameCounter + " size=" + width + "x" + height);
        postProcessing.beginFrame(); // set true again once this frame's HDR display image is written
        if (failed) {
            return false;
        }
        RtContext ctx = RtContext.get();
        if (ctx == null) {
            return false;
        }
        ctx.gpuExecutor().throwIfFailed();
        // Count-bounded terrain streaming (dispatch/drain/build kick) runs here once per render frame — before
        // the ready gate below, because it is what MAKES terrain ready during the initial fill.
        try {
            RtTerrain.frame(ctx);
        } catch (Throwable t) {
            ctx.gpuExecutor().throwIfFailed();
            failed = true;
            CausticaMod.LOGGER.error("RT terrain streaming failed; reverting to vanilla path", t);
            return false;
        }
        if (RtTerrain.currentOrNull() == null || !frameCaptured || Minecraft.getInstance().level == null) {
            // No world this frame (incl. after quitting to the title — terrain residency + frameCaptured can
            // linger until an explicit invalidate, which would otherwise present a stale/empty HDR image as a
            // black menu background). Skip RT so the present path falls back to vanilla SDR / the PQ SDR
            // convert path, which shows the menu + panorama correctly.
            return false;
        }
        try {
            postProcessing.ensurePipeline(ctx);
            // A resource reload re-stitches the block atlas. We've already torn down the world pipeline
            // (onResourceReloadStart) so nothing references the old atlas, but MC's deferred free keeps the
            // old view handle live for a few frames, then swaps in the new atlas (whose GPU upload may lag,
            // leaving the handle 0 transiently). Skip RT — vanilla renders — until the handle becomes a
            // fresh, non-zero value different from what we last bound; only then rebuild against it.
            if (!worldTraceResources.reloadReady()) {
                return false;
            }
            syncSharcResources(ctx);
            ensureOutput(ctx, width, height);
            // Cheap idempotent check every frame (not just on resize): if the exposure mode is switched
            // manual -> auto at runtime (video settings), the auto-mode histogram/state/pipeline must be
            // allocated before the post-processing exposure stage below needs them, or it throws.
            postProcessing.ensureExposure(ctx);
            worldTraceResources.ensureForFrame(ctx, traceFrameViews());
            if (worldTraceResources.consumeMaterialEpochTraceGate()) {
                return false;
            }
            worldTraceResources.refreshMaterialBindingsIfNeeded(ctx);
            updateMotion();
            FrameInputs inputs = prepareFrameInputs();
            frameContext = createFrameContext(inputs);
            pipelineContext = ctx;
            pipelineInputs = inputs;
            pipelineCursor = graphExecution.begin(frameContext);
            try {
                pipelineCursor.executeNext();
                recordFrame(ctx, nativeColor, inputs);
                if (!pipelineCursor.complete()) {
                    throw new IllegalStateException("frame pipeline did not execute every pass");
                }
            } finally {
                pipelineCursor = null;
                pipelineContext = null;
                pipelineInputs = null;
            }
            if (!loggedActive) {
                loggedActive = true;
                CausticaMod.LOGGER.info("RT composite active (terrain): {}x{}, RT output replaces the world target", width, height);
            }
            return true;
        } catch (Throwable t) {
            ctx.gpuExecutor().throwIfFailed();
            failed = true;
            CausticaMod.LOGGER.error("RT composite failed; reverting to vanilla path", t);
            return false;
        }
    }

    /**
     * Bring the world pipeline + LabPBR atlases up as soon as we're in a world and the block atlas is
     * loaded — <em>before</em> terrain tessellates — so the immutable material snapshot is available to
     * the first worker section. Driven from the client tick ahead of {@link RtTerrain#update}. No-op once
     * the pipeline exists, while a reload rebuild is pending (the reload path rebuilds against the new
     * atlas), or until we're in a world with the atlas ready. The heavy {@code _s}/{@code _n} atlases are
     * deliberately not built at the menu — only once a world is entered.
     */
    public void ensureResourcesReady(RtContext ctx) {
        if (failed) {
            return;
        }
        try {
            worldTraceResources.ensureAheadOfFrame(ctx, traceFrameViews());
        } catch (Throwable t) {
            failed = true;
            CausticaMod.LOGGER.error("RT resource bring-up failed; reverting to vanilla path", t);
        }
    }

    /**
     * Hooked at the HEAD of {@link net.minecraft.client.Minecraft#reloadResourcePacks()} (mixin). A
     * resource reload re-stitches the block atlas (and reloads entity textures): MC frees the old GPU
     * images via its deferred destruction queue, which refuses while any descriptor set still references
     * them ("in use by VkDescriptorSet" → device lost). So we drain in-flight frames and then <b>destroy
     * the world pipeline outright</b> — dropping every descriptor reference (block atlas binding 2 +
     * bindless set) — so MC can free its textures cleanly. The pipeline is cheap to rebuild (no terrain
     * re-upload); {@code ensureWorld} recreates it on the first world frame after the reload, once the new
     * atlas is ready (gated in {@link #composite}). The new material epoch clears terrain before trace.
     */
    public void onResourceReloadStart() {
        recordTemporalReset(TemporalResetReason.RESOURCE_RELOAD);
        recordTemporalReset(TemporalResetReason.MATERIAL_GENERATION_CHANGE);
        worldTraceResources.onResourceReload(
                () -> broadcastTemporalReset(RtEntities.INSTANCE::onResourceReload));
    }

    /** Borrowed frame-sized views used by the world descriptor owner. */
    private TraceFrameResources.TraceFrameViews frameViews() {
        return traceFrameResources.views();
    }

    private WorldTraceResources.FrameViews traceFrameViews() {
        TraceFrameResources.TraceFrameViews views = traceFrameResources.views();
        if (views == null || views.output() == null || views.normal() == null) {
            return null;
        }
        return new WorldTraceResources.FrameViews(views.output().view, new long[]{
                views.normal().view, views.albedo().view, views.depth().view, views.motion().view,
                views.specularAlbedo().view, views.specularMotion().view, views.viewZ().view,
                views.nrdDiffuseInput().view, views.nrdSpecularInput().view
        });
    }

    /**
     * Match the persistent ReSTIR allocation to the live player toggle. A state transition is deliberately
     * synchronous and rare: waiting idle first makes it impossible for a recorded BDA load to outlive the
     * buffers, then both ping-pong halves are either destroyed or freshly allocated and zero-filled. Thus
     * OFF releases the VRAM (rather than merely hiding it), and ON can never observe stale reservoirs.
     */
    private void syncRestirResources(RtContext ctx) {
        TraceFrameResources.TraceFrameViews views = traceFrameResources.views();
        restirSystem.sync(ctx, views.renderWidth(), views.renderHeight());
    }

    private void ensureOutput(RtContext ctx, int width, int height) {
        boolean rrEnabled = dlssRrBackend.available();
        int rrQuality = rrEnabled ? dlssRrBackend.quality() : Integer.MIN_VALUE;
        // FSR 3 only takes the upscale slot when RR is not running (the selector makes them
        // mutually exclusive, but a hand-edited config could enable both — RR wins).
        boolean fsrEnabled = !rrEnabled && upscalers.fsr().available();
        int fsrQuality = fsrEnabled ? upscalers.fsr().quality() : Integer.MIN_VALUE;
        // XeSS shares the slot under the same rules; if a hand-edit stacks them, RR > FSR > XeSS.
        boolean xessEnabled = !rrEnabled && !fsrEnabled && upscalers.xess().available();
        int xessQuality = xessEnabled ? upscalers.xess().quality() : Integer.MIN_VALUE;
        // The denoise slot. Exactly one denoiser ever runs on a frame, in this order:
        //   DLSS-RR (denoises internally, so nothing else may touch the image)
        //   > NRD/REBLUR (opt-in, needs bundled natives)
        //   > SVGF (the renderer's own; the default for every non-DLSS path).
        // Two temporal denoisers in series would fight over the same history and reintroduce exactly
        // the ghosting this rework removes, so they are strictly exclusive.
        // The quarantined NRD experiment is not selectable. Keeping this decision behind its
        // boundary prevents configuration or native-library availability from claiming the slot.
        boolean nrdEnabled = !rrEnabled && nrdBackend.selected();
        boolean svgfEnabled = !rrEnabled && !nrdEnabled && CausticaConfig.Rt.Denoise.ENABLED.value();
        TraceFrameResources.Configuration configuration = new TraceFrameResources.Configuration(
                new FrameContext.Extent(width, height), rrEnabled, rrQuality, fsrEnabled, fsrQuality,
                xessEnabled, xessQuality, svgfEnabled, nrdEnabled);
        if (traceFrameResources.matches(configuration)
                && postProcessing.imagesReady() && postProcessing.exposureReady()) {
            syncRestirResources(ctx);
            return;
        }
        recordTemporalReset(TemporalResetReason.RESOLUTION_CHANGE);
        ctx.waitIdle(); // resize is rare; no in-flight frame may use the old image/descriptor
        // Reaching here with RR off can mean the denoising filter was just turned off. Nothing calls
        // ensureFeature again in that state, so the RR feature (and its history buffers) would stay
        // allocated for the rest of the session; the device is idle right now, so release it here.
        dlssRrBackend.releaseIfDisabled();
        // Release inactive FSR/XeSS state at the same synchronized switch-away seam.
        upscalers.releaseInactiveBackends();
        postProcessing.releaseImagesForResize();
        traceFrameResources.releasePrimaryAfterIdle();
        restirSystem.destroy();
        traceFrameResources.releaseGuidesAfterIdle();
        svgfBackend.releaseResources();
        traceFrameResources.releaseReconstructionOutputsAfterIdle();

        // The path tracer + its guide buffers run at render res; the active upscaler — DLSS-RR
        // (denoise + upscale) or FSR 3 (upscale only) — or a fallback blit brings the image to
        // display res. With neither active there is no reconstruction pass, so trace at 1:1 for a
        // faithful reference. With one active, ask IT what render resolution its chosen quality
        // mode actually expects rather than assuming a fixed ratio: different quality modes (and
        // driver/SDK versions) use different ratios, and each upscaler's own query is the source
        // of truth for what its dispatch will accept.
        FrameContext.Extent optimal;
        if (rrEnabled) {
            optimal = dlssRrBackend.recommendedRenderExtent(width, height);
        } else if (fsrEnabled) {
            optimal = upscalers.fsr().recommendedRenderExtent(width, height);
        } else if (xessEnabled) {
            optimal = upscalers.xess().recommendedRenderExtent(width, height);
        } else {
            optimal = upscalers.nativeBackend().recommendedRenderExtent(width, height);
        }
        TraceFrameResources.TraceFrameViews views = traceFrameResources.createPrimary(ctx, configuration, optimal);
        syncRestirResources(ctx);
        postProcessing.createImages(ctx, width, height);
        views = traceFrameResources.createGuides(ctx);
        // SVGF working set: colour/frame-count history, luminance moments, and the à-trous
        // ping-pong (whose alpha carries variance), plus copies of last frame's depth/normal guides
        // so the reprojection can validate history against the geometry it came from.
        if (svgfEnabled) {
            svgfBackend.ensureResources(ctx, views.renderWidth(), views.renderHeight());
        }
        views = traceFrameResources.createReconstructionOutputs(ctx);
        // Denoiser outputs + the decoded/summed image the upscale stage consumes exist only while
        // NRD actually runs; the combine pipeline is created lazily with them.
        if (nrdEnabled) {
            // Re-modulation reads the same guides the tracer demodulated with (see nrd_combine.comp),
            // and the raw trace supplies the sky, which REBLUR does not denoise.
            nrdBackend.bindCombine(ctx, new ExperimentalNrdBackend.NrdFrameViews(
                    views.nrdDiffuseOutput().view, views.nrdSpecularOutput().view,
                    views.nrdCombined().view, views.output().view, views.albedo().view,
                    views.viewZ().view, views.specularAlbedo().view, views.normal().view));
        }
        postProcessing.ensureExposure(ctx);

        broadcastTemporalReset(() -> {
            if (svgfEnabled) {
                // Fresh buffers hold nothing the reprojection may read.
                svgfBackend.requestReset();
            }
            if (nrdEnabled) {
                // NRD's own temporal history cannot survive a resolution change either.
                nrdBackend.resetHistory();
            }
            mvHasPrev = false; // recreated images -> first MV frame is zero
            waterWaveTimeValid = false;
        });
        worldTraceResources.bindFrameViews(traceFrameViews());
        postProcessing.bind(views.rrOutput());
    }

    /**
     * Compute this frame's motion-vector push data: the matrix that projects a current world point
     * into the previous frame's clip space, plus the per-frame camera translation. On the first frame
     * (or after a reset) push the current view-projection with zero delta so MVs come out zero.
     */
    private void updateMotion() {
        mvCurProjView.set(frameProjection).mul(frameViewRotation);
        if (mvHasPrev) {
            mvPushMatrix.set(mvPrevProjView);
            previousFrameCamX = mvPrevCamX;
            previousFrameCamY = mvPrevCamY;
            previousFrameCamZ = mvPrevCamZ;
            mvCamDeltaX = (float) (camX - mvPrevCamX);
            mvCamDeltaY = (float) (camY - mvPrevCamY);
            mvCamDeltaZ = (float) (camZ - mvPrevCamZ);
        } else {
            mvPushMatrix.set(mvCurProjView);
            previousFrameCamX = camX;
            previousFrameCamY = camY;
            previousFrameCamZ = camZ;
            mvCamDeltaX = 0f;
            mvCamDeltaY = 0f;
            mvCamDeltaZ = 0f;
        }
        mvPrevProjView.set(mvCurProjView);
        mvPrevCamX = camX;
        mvPrevCamY = camY;
        mvPrevCamZ = camZ;
        mvHasPrev = true;
    }

    private void prepareFrame(FrameContext frame) {
        if (frame != frameContext || pipelineInputs == null) {
            throw new IllegalStateException("prepare pass has no active frame invocation");
        }
        temporalState.snapshot(frame);
        // FG reads the frame's jitter at present time (PREPARE wants the offset the rays used).
        frameGenerationResources.captureJitter(frame.jitter().x(), frame.jitter().y());
    }

    private void recordPathTrace(FrameContext frame) {
        if (frame != frameContext || pipelineContext == null || pipelineCommand == null
                || pipelineStack == null || pipelinePushConstants == null) {
            throw new IllegalStateException("path-trace pass has no active frame invocation");
        }
        dev.comfyfluffy.caustica.rt.graph.PathTraceBarrierPlan barrierPlan =
                dev.comfyfluffy.caustica.rt.graph.PathTraceBarrierPlan.create(
                        pipelineInputs.svgfPath() || pipelineInputs.nrdPath(), pipelineInputs.nrdPath());
        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(
                pipelineContext, pipelineCommand, "world primary trace");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.tracePrimary")) {
            worldTraceResources.trace(pipelineCommand, frameViews().renderWidth(), frameViews().renderHeight(), pipelinePushConstants, 0);
        }
        dev.comfyfluffy.caustica.rt.graph.PathTraceBarriers.before(
                pipelineCommand, pipelineStack, barrierPlan,
                dev.comfyfluffy.caustica.rt.graph.PathTraceBarrierPlan.INDIRECT);
        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(
                pipelineContext, pipelineCommand, "world indirect trace");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.traceIndirect")) {
            worldTraceResources.trace(pipelineCommand, frameViews().renderWidth(), frameViews().renderHeight(), pipelinePushConstants, 1);
        }
        dev.comfyfluffy.caustica.rt.graph.PathTraceBarriers.before(
                pipelineCommand, pipelineStack, barrierPlan,
                dev.comfyfluffy.caustica.rt.graph.PathTraceBarrierPlan.EXPORT);
        int barrierMode = 1;
        if (loggedPathTraceBarrierMode != barrierMode) {
            CausticaMod.LOGGER.info("AER-083 path-trace barriers: path={}, scope=legacy-conservative",
                    "generated");
            loggedPathTraceBarrierMode = barrierMode;
        }
    }

    private void reconstructFrame(FrameContext frame) {
        if (frame != frameContext || pipelineContext == null || pipelineInputs == null
                || pipelineCommand == null || pipelineStack == null || pipelineReconstructionInput == null) {
            throw new IllegalStateException("reconstruction pass has no active frame invocation");
        }
        RtContext ctx = pipelineContext;
        VkCommandBuffer cmd = pipelineCommand;
        MemoryStack stack = pipelineStack;
        boolean rrDone = false;
        RtImage upscaleSource = pipelineReconstructionInput.denoisedSource() != null
                ? pipelineReconstructionInput.denoisedSource() : frameViews().output();

        if (pipelineInputs.rrPath()) {
            dev.comfyfluffy.caustica.rt.reconstruction.ReconstructionResult result = dlssRrBackend.execute(
                    new DlssRrReconstructionBackend.Request(ctx, cmd, frameViews().output(), frameViews().depth(), frameViews().motion(), frameViews().albedo(),
                            frameViews().specularAlbedo(), frameViews().normal(), frameViews().specularMotion(), frameViews().rrOutput(),
                            frameViews().renderWidth(), frameViews().renderHeight(), frameViews().displayWidth(), frameViews().displayHeight(),
                            -frame.jitter().x(), -frame.jitter().y(), frameViewRotation, frameProjection));
            rrDone = result.executed();
            if (rrDone) {
                upscaleSource = result.output();
            }
        }

        boolean svgfRan = false;
        boolean svgfDebugView = SvgfReconstructionBackend.isDebugView(pipelineReconstructionInput.debugView());
        if (pipelineInputs.svgfPath() && !pipelineReconstructionInput.nrdDone()
                && !pipelineReconstructionInput.nrdValidationOn()
                && svgfBackend.available() && frameViews().viewZ() != null) {
            dev.comfyfluffy.caustica.rt.reconstruction.ReconstructionResult result = svgfBackend.execute(
                    new SvgfReconstructionBackend.Request(ctx, cmd, stack, upscaleSource,
                            frameViews().motion(), frameViews().viewZ(), frameViews().normal(), frameViews().albedo(), frameViews().renderWidth(), frameViews().renderHeight(),
                            svgfDebugView ? pipelineReconstructionInput.debugView() : 0,
                            frameViewRotation, camX, camY, camZ));
            upscaleSource = result.output();
            svgfRan = result.executed();
        }
        pipelineReconstructionResult = new ReconstructionResult(rrDone, upscaleSource, svgfRan);
    }

    private void upscaleFrame(FrameContext frame) {
        if (frame != frameContext || pipelineContext == null || pipelineInputs == null
                || pipelineCommand == null || pipelineStack == null || pipelineUpscaleInput == null) {
            throw new IllegalStateException("upscale pass has no active frame invocation");
        }
        RtContext ctx = pipelineContext;
        VkCommandBuffer cmd = pipelineCommand;
        MemoryStack stack = pipelineStack;
        boolean rrDone = pipelineUpscaleInput.rrDone();
        dev.comfyfluffy.caustica.rt.graph.UpscalerBarrierPlan.Backend barrierBackend =
                dev.comfyfluffy.caustica.rt.graph.UpscalerBarrierPlan.Backend.DLSS_RR;
        RtImage upscaleSource = pipelineUpscaleInput.upscaleSource();
        boolean svgfRan = pipelineUpscaleInput.svgfRan();
        boolean nrdDone = pipelineUpscaleInput.nrdDone();
        boolean fsrPath = pipelineInputs.fsrPath();
        boolean xessPath = pipelineInputs.xessPath();
        float jitterX = frame.jitter().x();
        float jitterY = frame.jitter().y();
        if (!rrDone && fsrPath) {
            float fovY = (float) (2.0 * Math.atan(1.0 / Math.abs(frameProjection.m11())));
            dev.comfyfluffy.caustica.rt.upscale.UpscaleResult result = upscalers.fsr().execute(
                    new FsrUpscalerBackend.Request(ctx, cmd, upscaleSource, frameViews().depth(), frameViews().motion(), frameViews().rrOutput(),
                            frameViews().renderWidth(), frameViews().renderHeight(), frameViews().displayWidth(), frameViews().displayHeight(), -jitterX, -jitterY, fovY,
                            camX, camY, camZ, () -> {
                                recordTemporalReset(TemporalResetReason.TELEPORT);
                                broadcastTemporalReset(upscalers.fsr()::requestReset);
                            }));
            rrDone = result.executed();
            if (rrDone) barrierBackend = dev.comfyfluffy.caustica.rt.graph.UpscalerBarrierPlan.Backend.FSR;
        }

        // Intel XeSS occupies the slot when neither RR nor FSR is running: same inputs as FSR
        // (denoised-or-raw color + depth + motion vectors), output straight into frameViews().rrOutput(). The
        // ML reconstruction replaces FSR's analytic pass — same upscale slot, same consumers.
        if (!rrDone && xessPath) {
            dev.comfyfluffy.caustica.rt.upscale.UpscaleResult result = upscalers.xess().execute(
                    new XessUpscalerBackend.Request(ctx, cmd, upscaleSource, frameViews().depth(), frameViews().motion(), frameViews().rrOutput(),
                            frameViews().renderWidth(), frameViews().renderHeight(), frameViews().displayWidth(), frameViews().displayHeight(), jitterX, jitterY,
                            svgfRan || nrdDone, camX, camY, camZ, () -> {
                                recordTemporalReset(TemporalResetReason.TELEPORT);
                                broadcastTemporalReset(upscalers.xess()::requestReset);
                            }));
            rrDone = result.executed();
            if (rrDone) barrierBackend = dev.comfyfluffy.caustica.rt.graph.UpscalerBarrierPlan.Backend.XESS;
        }

        // When no upscaler produced the display-res image (disabled, debug view, or a runtime
        // failure), bring the render-res trace up to display res with a linear blit so the display mapper
        // always has a display-res RT image. With no upscaler render == display, so this is a 1:1 copy.
        if (!rrDone) {
            upscalers.nativeBackend().execute(
                    new NativeUpscalerBackend.Request(ctx, cmd, stack, upscaleSource, frameViews().rrOutput()));
            barrierBackend = dev.comfyfluffy.caustica.rt.graph.UpscalerBarrierPlan.Backend.NATIVE;
        }
        dev.comfyfluffy.caustica.rt.graph.UpscalerBarriers.before(cmd, stack, barrierBackend, "export");
        String barrierMode = "generated, backend=" + barrierBackend;
        if (!barrierMode.equals(loggedUpscalerBarrierMode)) {
            CausticaMod.LOGGER.info("AER-083 upscaler barriers: path={}, scope=legacy-conservative", barrierMode);
            loggedUpscalerBarrierMode = barrierMode;
        }
    }

    private void postPresentFrame(FrameContext frame) {
        if (frame != frameContext || pipelineContext == null || pipelineCommand == null
                || pipelineStack == null || pipelinePostPresentTarget == 0L) {
            throw new IllegalStateException("post/present pass has no active frame invocation");
        }
        boolean postHdr = CausticaConfig.Rt.Hdr.enabled();
        postProcessing.record(pipelineContext, pipelineCommand, pipelineStack, frameViews().rrOutput(),
                frameViews().displayWidth(), frameViews().displayHeight(), pipelinePostPresentTarget, postHdr);
    }

    private FrameInputs prepareFrameInputs() {
        int debugView = debugView();
        boolean rrPath = dlssRrBackend.available() && debugView == 0;
        boolean fsrPath = !rrPath && upscalers.fsr().available() && debugView == 0;
        boolean xessPath = !rrPath && !fsrPath && upscalers.xess().available() && debugView == 0;
        boolean nrdPath = !rrPath && nrdBackend.selected() && debugView == 0;
        boolean svgfDebugView = SvgfReconstructionBackend.isDebugView(debugView);
        boolean svgfPath = !rrPath && CausticaConfig.Rt.Denoise.ENABLED.value()
                && (debugView == 0 || svgfDebugView);
        float jitterX = 0f;
        float jitterY = 0f;
        if (rrPath) {
            CausticaJitter.INSTANCE.prepare(frameViews().renderWidth(), frameViews().renderHeight(), frameViews().displayWidth());
            jitterX = CausticaJitter.INSTANCE.jitterPixelsX() * jitterSignX();
            jitterY = CausticaJitter.INSTANCE.jitterPixelsY() * jitterSignY();
        } else if (fsrPath) {
            CausticaJitter.INSTANCE.prepareFsr(frameViews().renderWidth(), frameViews().displayWidth());
            jitterX = CausticaJitter.INSTANCE.jitterPixelsX() * jitterSignX();
            jitterY = CausticaJitter.INSTANCE.jitterPixelsY() * jitterSignY();
        } else if (xessPath) {
            CausticaJitter.INSTANCE.prepareXess();
            jitterX = CausticaJitter.INSTANCE.jitterPixelsX() * jitterSignX();
            jitterY = CausticaJitter.INSTANCE.jitterPixelsY() * jitterSignY();
        } else if (nrdPath || svgfPath) {
            CausticaJitter.INSTANCE.prepareFsr(frameViews().renderWidth(), frameViews().displayWidth());
            jitterX = CausticaJitter.INSTANCE.jitterPixelsX() * jitterSignX();
            jitterY = CausticaJitter.INSTANCE.jitterPixelsY() * jitterSignY();
        }
        return new FrameInputs(rrPath, fsrPath, xessPath, nrdPath, svgfPath, jitterX, jitterY);
    }

    private FrameContext createFrameContext(FrameInputs inputs) {
        return new FrameContext(frameCounter,
                Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks() * 0.05f,
                new FrameContext.Extent(frameViews().displayWidth(), frameViews().displayHeight()), new FrameContext.Extent(frameViews().renderWidth(), frameViews().renderHeight()),
                new FrameContext.Camera(camX, camY, camZ, mvCurProjView),
                new FrameContext.Camera(previousFrameCamX, previousFrameCamY, previousFrameCamZ, mvPushMatrix),
                new FrameContext.Jitter(inputs.jitterX(), inputs.jitterY()), Minecraft.getInstance().level,
                dimensionId(Minecraft.getInstance().level), FrameContext.LEGACY_SCENE_GENERATION);
    }

    private void recordFrame(RtContext ctx, GpuTexture nativeColor, FrameInputs inputs) {
        long dstImage = vkImage(nativeColor);
        var encoder = (VulkanCommandEncoder) ((CommandEncoderAccessor) RenderSystem.getDevice().createCommandEncoder()).caustica$getBackend();
        RtGpuExecutor gpuExecutor = ctx.gpuExecutor();
        // Reserve the graphics-use value that guards this frame's reusable TLAS and entity resources.
        RtGpuExecutor.GraphicsUse graphicsUse = gpuExecutor.beginGraphicsUse(encoder);
        RtGpuExecutor.GraphicsUseWaiter graphicsUseWaiter = gpuExecutor.graphicsUseWaiter();
        pendingGraphicsUse = graphicsUse;
        RtEntities.EntitySceneContribution entityContribution = null;
        VkCommandBuffer cmd = encoder.allocateAndBeginTransientCommandBuffer();
        RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_COMMAND_BUFFER, cmd.address(), "composite command buffer");
        int debugView = debugView();
        RtTerrain terrain = RtTerrain.currentOrNull();
        try (MemoryStack stack = MemoryStack.stackPush(); RtDebugLabels.Scope frameLabel = RtDebugLabels.scope(ctx, cmd, "composite frame")) {
            // The active upscaler drives the frame: trace + jitter at render res, then DLSS-RR
            // (denoise+upscale) or FSR 3 (upscale only) brings it to display res. They occupy one
            // slot — RR wins if both are somehow on — and jitter is suppressed for the no-upscaler
            // reference and for the debug guide views (raw inspection).
            boolean rrPath = inputs.rrPath();
            boolean fsrPath = inputs.fsrPath();
            boolean xessPath = inputs.xessPath();
            // The denoise slot, in the same priority order ensureOutput allocated for:
            // RR > NRD > SVGF. Both denoisers want a jittered trace — their temporal stage
            // integrates the sub-pixel sequence, which is what resolves detail below the pixel grid
            // and lets the upscaler reconstruct it — and both are told the exact jitter used.
            boolean nrdPath = inputs.nrdPath();
            // SVGF is the fallback as well as the primary: if NRD is selected but its denoise call
            // fails this frame, the gate below (svgfPath && !nrdDone) lets SVGF take the slot
            // instead of presenting the raw trace. Its resources are allocated whenever
            // the quarantined NRD experiment is not selected.
            // Debug views 10-12 inspect the SVGF denoiser's own internal state (history length,
            // variance, luminance sigma), so unlike the guide views they must keep it RUNNING.
            // They exist because four rounds of fixes reasoned from the source produced no visible
            // change for the user; the filter's state has to be measured in the actual frame.
            boolean svgfDebugView = SvgfReconstructionBackend.isDebugView(debugView);
            boolean svgfPath = inputs.svgfPath();
            float jitterX = inputs.jitterX();
            float jitterY = inputs.jitterY();

            // Optional coarse LOD proxy (Distant Horizons / Voxy). A no-op when neither mod is present.
            RtLodTerrain.INSTANCE.frame(ctx, terrain.blockX, terrain.blockY, terrain.blockZ);
            // Select the next BDA ring slot; the generated WorldPushData serializer fills it once all
            // frame-derived values (including entity addresses and block-breaking entries) are known.
            RtBuffer pushBuf = worldTraceResources.acquirePushBuffer(graphicsUse, graphicsUseWaiter);
            ByteBuffer push = MemoryUtil.memByteBuffer(pushBuf.mapped, WORLD_PUSH_SIZE);
            // Exact per-section vanilla-readiness hand-off mask for world.rahit's DH/Voxy suppression.
            ByteBuffer readyMask = MemoryUtil.memByteBuffer(
                    pushBuf.mapped + READY_MASK_OFFSET, READY_MASK_CAPACITY)
                    .order(ByteOrder.nativeOrder());
            int readyMaskBytes = RtTerrain.writeDistantReadyMask(readyMask);
            long readyMaskAddress = readyMaskBytes == 0
                    ? 0L : pushBuf.deviceAddress + READY_MASK_OFFSET;
            // Vanilla's authored cloud shape for the classic deck (CloudModule), re-published into
            // whichever ring slot this frame uses. 8 KiB of words — the copy is rounding error next to
            // the push itself; doing it every frame keeps all six slots valid instead of tracking which
            // slot last received the map. Address 0 when no usable clouds.png exists, which the shader
            // reads as "fall back to the noise deck" — a resource pack can never remove the clouds.
            int[] cloudCells = cloudModule.cells();
            long cloudCellsAddress = 0L;
            if (cloudCells != null) {
                ByteBuffer cellsBuf = MemoryUtil.memByteBuffer(
                        pushBuf.mapped + CLOUD_CELLS_OFFSET, CloudModule.MAP_BYTES)
                        .order(ByteOrder.nativeOrder());
                cellsBuf.asIntBuffer().put(cloudCells, 0, CloudModule.MAP_WORDS);
                cloudCellsAddress = pushBuf.deviceAddress + CLOUD_CELLS_OFFSET;
            }
            frameInvViewProj.set(frameProjection).mul(frameViewRotation).invert();
            // flags: camera-in-water (so the path tracer starts in the water medium when the eye is
            // submerged, fixing the air→water first-segment orientation) + W1 geometric waves. Bit 1 used to
            // gate a Lambertian fallback BRDF that nothing ever turned off; the GGX path is unconditional
            // now, so that bit is unused rather than reassigned, to avoid a stale reader elsewhere.
            // This word describes the frame's PHYSICAL state; player-facing effect toggles live in the
            // separate featureFlags word (see featureFlags()).
            int flags = 0;
            var level = Minecraft.getInstance().level;
            if (level != null) {
                cameraBlockPos.set(Mth.floor(camX), Mth.floor(camY), Mth.floor(camZ));
                // Height-aware, mirroring vanilla's own Camera.getFluidInCamera(): a plain block-granular
                // test wrongly flags the eye submerged anywhere in a water column's top block, even well
                // above its actual surface (shallow/flowing water, or standing with your head just over a
                // source block).
                FluidState fs = level.getFluidState(cameraBlockPos);
                if (fs.is(FluidTags.WATER) && camY < cameraBlockPos.getY() + fs.getHeight(level, cameraBlockPos)) {
                    flags |= 0b01;
                }
            }
            if (waterWaves()) {
                flags |= 0b10000; // W1: animated geometric water waves
            }
            if (CausticaConfig.Rt.Composite.PARALLAX_SMOOTHING.value()) {
                flags |= 0b100000; // bit5: bilinear LabPBR normal/surface sampling (POM columns stay texel-exact)
            }

            // W1/W2 water parameters: camera-biome tint plus wrapped animation time. Per-water-body tint
            // comes from the primitive; this is the fallback for a camera already inside the medium.
            float wtr = 0.25f, wtg = 0.46f, wtb = 0.9f; // neutral ocean-ish default if no level/biome
            if (level != null) {
                int wc = BiomeColors.getAverageWaterColor(level, cameraBlockPos);
                wtr = ((wc >> 16) & 0xFF) / 255f;
                wtg = ((wc >> 8) & 0xFF) / 255f;
                wtb = (wc & 0xFF) / 255f;
            }
            float waterWaveTime = (float) (System.nanoTime() / 1.0e9 % 3600.0);
            float waterWaveDelta = waterWaveTime - previousWaterWaveTime;
            // A first frame, long pause, or one-hour phase wrap has no adjacent wave frame to reproject.
            // Use the current phase so the reflection MV is neutral instead of manufacturing a huge jump.
            float priorWaterWaveTime = waterWaveTimeValid
                    && waterWaveDelta >= 0f && waterWaveDelta <= 0.25f
                    ? previousWaterWaveTime : waterWaveTime;
            previousWaterWaveTime = waterWaveTime;
            waterWaveTimeValid = true;
            Float4 waterParams = new Float4(wtr, wtg, wtb, waterWaveTime);
            // W1 wave-domain anchor: the terrain rebase origin reduced mod 4096 (kept small for shader
            // float precision). hitPos.xz (rebased) + anchor reconstructs a world-pinned coordinate, so the
            // ripple pattern stays fixed in the world as the player moves and the rebase origin shifts.
            Float4 waterAnchor = new Float4(terrain.blockX & WATER_ANCHOR_MASK,
                    terrain.blockZ & WATER_ANCHOR_MASK, priorWaterWaveTime, 0f);

            // Rebuild the TLAS this frame from static section instances merged with dynamic entity
            // instances, bind it into the pipeline's descriptor ring, record the build, then barrier so
            // the trace sees the finished TLAS. Section BLASes are already built (async, by RtTerrain);
            // only the cheap instance-level TLAS is rebuilt per frame. Retired terrain geometry/table
            // generations are reclaimed by graphics-timeline completion.
            // Entity BLASes are built inline below and merged into the per-frame TLAS. geomTableAddr
            // feeds the hit shader entity path (per-prim normal/tint) and motion vectors.
            TerrainSceneContribution terrainContribution = terrain.sceneContribution();
            LodSceneContribution lodContribution = RtLodTerrain.INSTANCE.sceneContribution(
                    terrain.blockX, terrain.blockY, terrain.blockZ);
            var staticInstances = SceneAssembler.INSTANCE.staticInstances(terrainContribution, lodContribution);
            RtEntities.EntitySceneContribution fe = RtEntities.INSTANCE.beginFrame(ctx, staticInstances,
                    terrain.blockX, terrain.blockY, terrain.blockZ, camX, camY, camZ, frameProjection, frameViewRotation);
            entityContribution = fe;
            RtScene scene = SceneAssembler.INSTANCE.assemble(
                    terrainContribution, lodContribution, fe,
                    new RtScene.LightView(
                            terrain.lightBufferAddress(), terrain.lightAliasBufferAddress(),
                            terrain.lightLocalAliasBufferAddress(), terrain.lightGridCellBufferAddress(),
                            terrain.lightGridSpanBufferAddress(), terrain.lightCount(), terrain.lightGeneration()),
                    new RtScene.MaterialView(
                            RtMaterialRegistry.INSTANCE.tableAddress(), RtMaterialRegistry.INSTANCE.epoch()),
                    RtScene.LEGACY_SCENE_GENERATION);
            // Block-breaking overlay: resolves each destroy-stage RenderType's texture into the
            // SAME bindless entity-texture array (destroy_stage_N.png is a standalone Sampler0 texture,
            // not a block-atlas sprite — see ModelBakery.BREAKING_LOCATIONS/DESTROY_TYPES), so any newly
            // resolved slot rides along with the uploadPending() call right below.
            BreakEntry[] breaking = breakingEntries(terrain);
            // Dimension + weather drive the sky model and the celestial light, so both are resolved
            // together, once, from the same level and partial tick.
            EnvironmentParameters environment = environmentParameters(level);
            // Analytic held-item light: position + intensity lane and the item's RGB tint; w == 0
            // disables the shader term (toggle off, no luminous item, or no player).
            HandLightState hand = handLightState(terrain);
            SharcRadianceCache.Bindings sharcBindings = sharc.bindings(terrain);
            RestirSystem.Bindings restirBindings = restirSystem.bindings();
            new WorldPushData(
                    frameInvViewProj,
                    new Float3((float) (camX - terrain.blockX), (float) (camY - terrain.blockY),
                            (float) (camZ - terrain.blockZ)),
                    (int) frameCounter,
                    mvPushMatrix,
                    new Float3(mvCamDeltaX, mvCamDeltaY, mvCamDeltaZ),
                    spp(),
                    new Float2(jitterX, jitterY),
                    flags,
                    maxBounces(),
                    environment.sky().sunDir(),
                    environment.sky().lightDir(),
                    environment.sky().lightRadiance(),
                    environment.sky().moonDir(),
                    environment.sky().celestial(),
                    environment.sky().sunUv(),
                    environment.sky().moonUv(),
                    waterParams,
                    waterAnchor,
                    mvCurProjView,
                    breaking.length,
                    breaking,
                    // RIS emitter NEE: candidate count (0 = emitter NEE off; the shader also requires
                    // lightCount > 0, so an empty buffer degrades to legacy gather). The light buffer
                    // device addresses themselves are pc.light*Addr — every 64-bit address lives in the
                    // push-constant block now, not here.
                    new Float4(terrain.lightRebaseOffsetX(), terrain.lightRebaseOffsetY(),
                            terrain.lightRebaseOffsetZ(), terrain.lightInvGlobalPowerSum()),
                    new Float4(terrain.lightGridOriginX(), terrain.lightGridOriginY(), terrain.lightGridOriginZ(), 16f),
                    new Int4(terrain.lightGridDimX(), terrain.lightGridDimY(), terrain.lightGridDimZ(), 0),
                    terrain.lightCount(),
                    CausticaConfig.Rt.Lights.RIS_CANDIDATES.value(),
                    new Float4(CausticaConfig.Rt.Lights.BLOCK_INTENSITY.value(),
                            CausticaConfig.Rt.Lights.DYNAMIC_INTENSITY.value(),
                            0.0f, 0.0f),
                    new Float4(environment.weather().rain(), environment.weather().thunder(),
                            environment.weather().skyDarken(), environment.weather().lightAttenuation()),
                    environment.clouds().params(),
                    environment.clouds().anchor(),
                    environment.clouds().color(),
                    cloudCellsAddress,
                    // Shader-only POM: x relief depth (blocks), y max texel crossings, w fade distance.
                    parallaxParams(),
                    environment.dimension(),
                    featureFlags(),
                    // Analytic held-item light: xyz rebased position, w intensity (0 = none held),
                    // then the item's RGB tint lane.
                    hand.light(),
                    hand.color(),
                    // Water lanes (WorldPush.waterOpacity): x = extra neutral per-block extinction
                    // scale (0 = default clarity); y/z/w = live Animated Water tuning the spectrum
                    // multiplies in (height scale, speed scale, wave count) — 1/1/7 = authored look.
                    new Float4(CausticaConfig.Rt.Composite.WATER_OPACITY.value(),
                            CausticaConfig.Rt.Composite.WATER_WAVE_STRENGTH.value(),
                            CausticaConfig.Rt.Composite.WATER_WAVE_SPEED.value(),
                            (float) CausticaConfig.Rt.Composite.WATER_WAVE_DETAIL.value()),
                    // Material appearance lane: x is the optional metallic polish amount. It is read
                    // every frame so dragging the slider needs neither a material rebuild nor reload.
                    new Float4(CausticaConfig.Rt.Composite.METALLIC_SHININESS.value(), 0.0f, 0.0f, 0.0f),
                    sharcBindings.cacheAddress(),
                    sharcBindings.params(),
                    sharcBindings.params2(),
                    sharcBindings.params3(),
                    sharcBindings.gridOrigin(),
                    restirBindings.tuning(),
                    // Volumetric fog (WorldPush.fogParams): density lane zero when the toggle is
                    // off, so "off" costs the shader one comparison — see FogModule.
                    environment.fog().params(),
                    // Biome/weather tint for the fog's scatter (WorldPush.fogTint): the game's
                    // own FOG_COLOR attribute, blended by the slider — see FogModule.
                    environment.fog().tint()
            ).write(push);
            int flushBytes = Math.max(WORLD_PUSH_SIZE, READY_MASK_OFFSET + readyMaskBytes);
            if (cloudCellsAddress != 0L) {
                flushBytes = Math.max(flushBytes, CLOUD_CELLS_OFFSET + CloudModule.MAP_BYTES);
            }
            pushBuf.flush(0L, flushBytes);
            // Upload any entity textures registered this frame into the bindless set before the trace.
            worldTraceResources.uploadPendingEntityTextures(ctx);
            // Build the entity BLAS, the TLAS that references it and the terrain BLAS, then the trace.
            // Barriers separate each stage; the graphics-use timeline guards resource reuse.
            if (!fe.blas().isEmpty()) {
                try (RtFrameStats.Scope ignored = RtFrameStats.FRAME.stage("entity.blasRecord")) {
                    ctx.accelerationStructures().recordBuilds(ctx, cmd, fe.blas());
                }
                VulkanCommandEncoder.memoryBarrier(cmd, stack); // entity BLAS writes visible to the TLAS build
            }
            RtAccel.PreparedTlas frameTlas;
            try (RtFrameStats.Scope ignored = RtFrameStats.FRAME.stage("frame.prepareTlas")) {
                SceneAssembler.TlasInput tlasInput = SceneAssembler.INSTANCE.tlasInput(scene);
                frameTlas = ctx.accelerationStructures().buildTlas(
                        ctx, tlasInput.baseInstances(), tlasInput.dynamicInstances(), graphicsUse);
            }
            worldTraceResources.bindTlas(frameTlas.accel.handle, graphicsUse, graphicsUseWaiter);
            currentTlasHandle = frameTlas.accel.handle;
            try (RtFrameStats.Scope ignored = RtFrameStats.FRAME.stage("frame.recordTlas")) {
                ctx.accelerationStructures().recordTlas(ctx, cmd, frameTlas);
            }
            VulkanCommandEncoder.memoryBarrier(cmd, stack); // TLAS build visible to the trace

            // Push the BDA ring slot's address plus the small hot subset used directly by the shaders.
            // Every 64-bit device address the trace needs lives here, not behind worldPushAddr: the
            // section/entity/material tables are read from world.rahit/world.rchit, which never load
            // WorldPush at all, and the RIS light buffers are read from world.rgen's hot inner loop, so
            // none of them should cost an extra BDA dereference to find.
            ByteBuffer pushConstants = stack.malloc(WorldPushConstantsData.BYTE_SIZE);
            new WorldPushConstantsData(pushBuf.deviceAddress, terrain.tableAddress(), fe.geomTableAddr(),
                    lodContribution.tableAddress(), readyMaskAddress,
                    RtMaterialRegistry.INSTANCE.tableAddress(),
                    terrain.lightBufferAddress(), terrain.lightAliasBufferAddress(),
                    terrain.lightLocalAliasBufferAddress(), terrain.lightGridCellBufferAddress(),
                    terrain.lightGridSpanBufferAddress(), frameViews().continuationQueue().deviceAddress,
                    restirBindings.previousAddress(), restirBindings.currentAddress(),
                    // The SVGF debug ids are consumed by the denoiser, not the tracer: forwarding
                    // them would make the raygen paint a guide overlay over the very image we are
                    // trying to inspect. The tracer sees 0 (normal shading) for those.
                    (int) frameCounter, svgfDebugView ? 0 : debugView,
                    terrain.lightGeneration(), restirBindings.mode()).write(pushConstants);
            pipelineCommand = cmd;
            pipelineStack = stack;
            pipelinePushConstants = pushConstants;
            try {
                pipelineCursor.executeNext();
            } finally {
                pipelineCommand = null;
                pipelineStack = null;
                pipelinePushConstants = null;
            }
            // A FOV change does NOT need to restart accumulation, so nothing here does.
            //
            // The history is fetched through the motion vectors, and the tracer builds those with
            // prevViewProj -- the PREVIOUS frame's projection, carrying the previous frame's FOV.
            // A zoom therefore appears in the motion vector as the on-screen displacement it
            // actually is (a 1 degree step moves an edge pixel 5.9 px, a 7 degree step 38 px), the
            // bilinear fetch follows it, and the geometry gate validates the result. There is no
            // stale-projection error left for a reset to protect against.
            //
            // Resetting instead cost the whole screen at once: every pixel dropped from the
            // 48-frame window (14% residual noise) to a single sample (100%), and vanilla eases
            // the sprint FOV over about three frames, so it fired three times in a row. That flash
            // is what remained when starting/stopping a sprint and when toggling flight, after the
            // bob-invariant test correctly stopped firing during steady movement.
            //
            // Two real discontinuities still restart it, and they are handled where they arise
            // rather than by inspecting the matrix: the first frame after (re)allocation, via
            // the SVGF backend's history state, and a terrain rebase, which the retained NRD experiment compensates against
            // the anchor. Resolution changes reallocate, which takes the same path.

            // ---- NRD / REBLUR (opt-in). Consumes the tracer's demodulated per-lobe signals plus
            // the guides at render res; the combine pass re-modulates and sums the denoised pair
            // into frameViews().nrdCombined(). When it runs it owns the denoise slot: SVGF steps aside below,
            // because two temporal denoisers in series fight over the same history.
            boolean nrdDone = false;
            RtImage denoisedSource = null;
            if (nrdPath && frameViews().viewZ() != null && frameViews().nrdDiffuseOutput() != null) {
                try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, "NRD denoise");
                     RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.nrd")) {
                    // The camera goes in as ABSOLUTE world coordinates plus the terrain anchor the
                    // signals live in. That pair is what lets the denoiser compensate a rebase
                    // instead of seeing it as a teleport: the old code passed
                    // only anchor-relative coordinates, so every rebase silently invalidated
                    // REBLUR's history mid-motion. No FOV-driven restart is passed: the motion
                    // vectors already carry a zoom as screen displacement (see the SVGF path).
                    nrdDone = nrdBackend.denoise(cmd.address(), frameViews().renderWidth(), frameViews().renderHeight(),
                            frameViews().motion(), frameViews().normal(), frameViews().viewZ(), frameViews().nrdDiffuseInput(), frameViews().nrdSpecularInput(), frameViews().nrdDiffuseOutput(), frameViews().nrdSpecularOutput(),
                            frameViews().nrdValidation(),
                            frameProjection, frameViewRotation,
                            camX, camY, camZ,
                            terrain.blockX, terrain.blockY, terrain.blockZ,
                            jitterX, jitterY, (int) frameCounter, false);
                }
                if (nrdDone) {
                    DenoiserBarrierPlan nrdBarrierPlan = DenoiserBarrierPlan.nrd();
                    DenoiserBarriers.before(cmd, stack, nrdBarrierPlan,
                            DenoiserBarrierPlan.NRD_COMBINE);
                    try (RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.nrdCombine")) {
                        nrdBackend.combine(cmd, frameViews().renderWidth(), frameViews().renderHeight());
                    }
                    DenoiserBarriers.before(cmd, stack, nrdBarrierPlan,
                            DenoiserBarrierPlan.NRD_EXPORT);
                    int denoiserMode = 1;
                    if (loggedDenoiserBarrierMode != denoiserMode) {
                        CausticaMod.LOGGER.info("AER-083 denoiser barriers: path={}, backend=NRD, scope=legacy-conservative",
                                "generated");
                        loggedDenoiserBarrierMode = denoiserMode;
                    }
                    denoisedSource = frameViews().nrdCombined();
                }
            }

            // Validation mode: REBLUR's 16-viewport diagnostic overlay replaces the image (set the
            // upscaler to Off for a crisp readout). Nothing downstream may filter the overlay.
            boolean nrdValidationOn = nrdDone && CausticaConfig.Rt.Nrd.VALIDATION.value();
            if (nrdValidationOn) {
                denoisedSource = frameViews().nrdValidation();
            }

            pipelineCommand = cmd;
            pipelineStack = stack;
            pipelineReconstructionInput = new ReconstructionInput(
                    denoisedSource, nrdDone, nrdValidationOn, debugView);
            pipelineReconstructionResult = null;
            try {
                pipelineCursor.executeNext();
            } finally {
                pipelineCommand = null;
                pipelineStack = null;
                pipelineReconstructionInput = null;
            }
            ReconstructionResult reconstruction = pipelineReconstructionResult;
            pipelineReconstructionResult = null;
            if (reconstruction == null) {
                throw new IllegalStateException("reconstruction pass produced no result");
            }
            boolean rrDone = reconstruction.rrDone();
            RtImage upscaleSource = reconstruction.upscaleSource();
            boolean svgfRan = reconstruction.svgfRan();
            pipelineCommand = cmd;
            pipelineStack = stack;
            pipelineUpscaleInput = new UpscaleInput(rrDone, upscaleSource, svgfRan, nrdDone);
            try {
                pipelineCursor.executeNext();
            } finally {
                pipelineCommand = null;
                pipelineStack = null;
                pipelineUpscaleInput = null;
            }

            pipelineCommand = cmd;
            pipelineStack = stack;
            pipelinePostPresentTarget = dstImage;
            try {
                pipelineCursor.executeNext();
            } finally {
                pipelineCommand = null;
                pipelineStack = null;
                pipelinePostPresentTarget = 0L;
            }
        }
        if (VK10.vkEndCommandBuffer(cmd) != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkEndCommandBuffer(rt composite) failed");
        }
        encoder.execute(cmd); // deferred into the frame's submission — correct for per-frame work
        // Submission order on the one graphics queue is the history dependency: next frame reads the half
        // this frame just wrote and writes the other half. Advance only after execute accepted the command.
        restirSystem.advance();
        // Do not attach a merely reserved token: failed recording may never signal it. Once execute succeeds,
        // every owner in this frame's manifest is protected through the final overlay consumer.
        RtEntities.INSTANCE.markGraphicsUse(entityContribution, graphicsUse);
    }

    /**
     * Block-breaking overlay: mirrors vanilla's {@code ClientLevel.destructionProgress()} (populated
     * by network packets, independent of the cancelled {@code LevelRenderer.render()} — see
     * [[rt-native-overlay-tier1]]) into the push's {@code breaking[]} list, so {@code world.rchit} can blend
     * the matching destroy-stage crack texture into a hit terrain block's albedo. Each block's own
     * destroy-stage texture ({@code minecraft:textures/block/destroy_stage_N.png}, resolved via
     * {@link ModelBakery#DESTROY_TYPES}) is a standalone {@code Sampler0} texture, not a block-atlas sprite,
     * so it rides the same bindless entity-texture array as entity textures ({@link RtEntityTextures}).
     */
    private BreakEntry[] breakingEntries(RtTerrain terrain) {
        BreakEntry[] result = new BreakEntry[WorldPushData.BREAKING_CAPACITY];
        int count = 0;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (var entry : level.destructionProgress().long2ObjectEntrySet()) {
                if (count >= result.length) {
                    break;
                }
                var progresses = entry.getValue();
                if (progresses == null || progresses.isEmpty()) {
                    continue;
                }
                int stage = Mth.clamp(progresses.last().getProgress(), 0, 9);
                BlockPos pos = BlockPos.of(entry.getLongKey());
                int slot = RtEntityTextures.INSTANCE.slotFor(ModelBakery.DESTROY_TYPES.get(stage));
                result[count++] = new BreakEntry(new Int4(
                        pos.getX() - terrain.blockX,
                        pos.getY() - terrain.blockY,
                        pos.getZ() - terrain.blockZ,
                        slot));
            }
        }
        return count == result.length ? result : java.util.Arrays.copyOf(result, count);
    }


    private record FrameInputs(boolean rrPath, boolean fsrPath, boolean xessPath, boolean nrdPath,
                               boolean svgfPath, float jitterX, float jitterY) {}

    private record ReconstructionInput(RtImage denoisedSource, boolean nrdDone,
                                       boolean nrdValidationOn, int debugView) {}

    private record ReconstructionResult(boolean rrDone, RtImage upscaleSource, boolean svgfRan) {}

    private record UpscaleInput(boolean rrDone, RtImage upscaleSource, boolean svgfRan, boolean nrdDone) {}

    /**
     * This frame's weather, resolved once on the CPU and pushed to the shaders.
     *
     * <p>{@code rain} and {@code thunder} are vanilla's own interpolated 0..1 levels. The two derived
     * multipliers exist so the sky shader and the NEE light cannot disagree about how dark a storm is:
     * {@code skyDarken} scales the atmosphere in-scatter in {@code world.rmiss}, {@code lightAttenuation}
     * scales the sun/moon radiance in {@link #skyPush}, and both are computed here, from the same two
     * levels, in one place.
     *
     * @param rain             vanilla rain level, 0 clear .. 1 fully raining
     * @param thunder          vanilla thunder level, 0 .. 1 (only ever non-zero while it is also raining)
     * @param skyDarken        multiplier on the sky's own radiance
     * @param lightAttenuation multiplier on the direct sun/moon radiance
     */
    /**
     * Read vanilla's interpolated rain/thunder levels and turn them into the sky/light multipliers.
     *
     * <p>The curve: overcast rain keeps about 35% of the clear-sky light and 45% of the sky's own
     * radiance, and a full thunderstorm roughly halves each of those again. Those numbers are picked to
     * match how vanilla treats the two states — rain drops the effective sky light level from 15 to 12
     * and a thunderstorm to 10, which is a much larger perceptual drop than the raw light levels suggest
     * because the sun disc is also gone — while staying well clear of zero, since a path tracer with no
     * sun and no sky fill has no light left at all and a daytime storm would render as night.
     *
     * <p>Thunder is folded in as an additional factor rather than a separate branch: vanilla only ever
     * raises the thunder level while it is already raining, so the two multiply into one continuous ramp
     * from clear to storm with no discontinuity at the transition.
     *
     * <p>Dimensions without weather (Nether, End) always report clear — {@code getRainLevel} is already
     * zero there, but returning the shared constant keeps the fast path allocation-free.
     */
    private static EnvironmentParameters.Weather weatherState(ClientLevel level, float partial) {
        if (level == null || !CausticaConfig.Rt.Composite.WEATHER_LIGHTING.value()) {
            return EnvironmentParameters.Weather.CLEAR;
        }
        float rain = Math.clamp(level.getRainLevel(partial), 0f, 1f);
        if (rain <= 0f) {
            return EnvironmentParameters.Weather.CLEAR;
        }
        // getThunderLevel already includes the rain level as a factor in vanilla; clamp defensively so a
        // datapack or mod that drives it independently cannot push the multipliers negative.
        float thunder = Math.clamp(level.getThunderLevel(partial), 0f, 1f);
        // Written out rather than via a lerp helper so each ramp's endpoints are readable inline:
        // at full rain the direct light keeps 35% and the sky 45%; a full thunderstorm then halves
        // each again (to ~18% and ~25% of clear).
        float rainLight = 1.0f - 0.65f * rain;
        float stormLight = 1.0f - 0.50f * thunder;
        float rainSky = 1.0f - 0.55f * rain;
        float stormSky = 1.0f - 0.45f * thunder;
        return new EnvironmentParameters.Weather(rain, thunder, rainSky * stormSky, rainLight * stormLight);
    }

    private EnvironmentParameters environmentParameters(ClientLevel level) {
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double gameTimeTicks = level == null ? 0.0 : level.getGameTime() + partial;
        EnvironmentParameters.Time time = new EnvironmentParameters.Time(partial, gameTimeTicks);
        int dimension = dimensionId(level);
        EnvironmentParameters.Weather weather = weatherState(level, partial);
        EnvironmentParameters.Sky sky = skyPush(dimension, weather, time);
        EnvironmentParameters.Clouds clouds = cloudModule.parameters(
                dimension, weather, camX, camY, camZ, time);
        EnvironmentParameters.Fog fog = fogModule.parameters(partial);
        return new EnvironmentParameters(dimension, time, weather, sky, fog, clouds);
    }

    /** Baseline radiance*area product of the analytic held-item light at light level 15. Calibrated so
     * a default-scale torch in hand reads close to a placed torch on nearby blocks; the Video Settings
     * held-item slider (lights.dynamic-intensity / {@code lightScales.y}) scales it live. */
    private static final float HAND_LIGHT_POWER = 0.6f;

    /** Per-item held-light tints (linear RGB). Mirrors and extends the well-known luminous sprite list
     * in {@code RtEntityCollector.itemSpriteEmission}, so the analytic light matches the colour the
     * captured item geometry already suggests. */
    private static final float[] TINT_SOUL = {0.35f, 0.85f, 1.0f};        // soul torch / lantern / campfire
    private static final float[] TINT_REDSTONE = {1.0f, 0.25f, 0.15f};    // redstone torch
    private static final float[] TINT_AQUA = {0.65f, 0.90f, 1.0f};        // sea lantern
    private static final float[] TINT_PICKLE = {0.75f, 0.95f, 0.55f};     // sea pickle
    private static final float[] TINT_TORCH = {1.0f, 0.62f, 0.30f};       // torch / lantern / campfire / candles
    private static final float[] TINT_LAVA = {1.0f, 0.35f, 0.10f};        // lava, magma
    private static final float[] TINT_GLOWSTONE = {1.0f, 0.78f, 0.50f};
    private static final float[] TINT_SHROOMLIGHT = {1.0f, 0.55f, 0.35f};
    private static final float[] TINT_VERDANT = {0.70f, 1.0f, 0.60f};     // verdant froglight
    private static final float[] TINT_PEARLESCENT = {0.95f, 0.70f, 1.0f}; // pearlescent froglight
    private static final float[] TINT_OCHRE = {1.0f, 0.85f, 0.50f};       // ochre froglight
    private static final float[] TINT_END_ROD = {1.0f, 0.95f, 0.85f};
    private static final float[] TINT_BEACON = {0.85f, 0.95f, 1.0f};
    private static final float[] TINT_AMBER = {1.0f, 0.60f, 0.30f};       // redstone lamp, jack o'lantern
    private static final float[] TINT_CRYING = {0.70f, 0.40f, 1.0f};      // crying obsidian
    private static final float[] TINT_BLAZE = {1.0f, 0.50f, 0.20f};
    private static final float[] TINT_DEFAULT = {1.0f, 0.75f, 0.55f};     // any other luminous block

    /** Per-frame held-light push pair: position + intensity lane, and the item's tint. */
    private record HandLightState(Float4 light, Float4 color) {
        static final HandLightState NONE = new HandLightState(
                new Float4(0.0f, 0.0f, 0.0f, 0.0f), new Float4(0.0f, 0.0f, 0.0f, 0.0f));
    }

    /**
     * The analytic point light a luminous held item casts (WorldPush.handLight +
     * WorldPush.handLightColor). Held-item geometry never enters the RIS emitter light buffer —
     * that buffer collects terrain quads only — so its captured flame would light the scene through
     * rare indirect bounce hits alone: a torch in hand barely brightened the blocks right in front
     * of it. The shader therefore NEE-samples this light at every diffuse receiver, exactly like
     * the celestial light.
     *
     * <p>Position follows the player's (partial-tick interpolated) view, pushed forward and slightly
     * below eye level — roughly where the held item sits in both first and third person. Intensity
     * and tint come from the brighter of main/off hand. The feature toggle, a missing player or no
     * luminous item all push {@code w == 0}, which disables the shader term entirely.
     */
    private static HandLightState handLightState(RtTerrain terrain) {
        if (!CausticaConfig.Rt.Lights.HELD_ITEM_LIGHT.value()) {
            return HandLightState.NONE;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return HandLightState.NONE;
        }
        HeldLight main = heldLight(player.getMainHandItem());
        HeldLight off = heldLight(player.getOffhandItem());
        HeldLight best = off.level() > main.level() ? off : main;
        if (best.level() <= 0) {
            return HandLightState.NONE;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        double hx = eye.x + look.x * 0.7;
        double hy = eye.y + look.y * 0.7 - 0.35;
        double hz = eye.z + look.z * 0.7;
        float intensity = (best.level() / 15.0f) * HAND_LIGHT_POWER;
        return new HandLightState(
                new Float4((float) (hx - terrain.blockX), (float) (hy - terrain.blockY),
                        (float) (hz - terrain.blockZ), intensity),
                new Float4(best.r(), best.g(), best.b(), 0.0f));
    }

    /** A held item's light: vanilla block-light level plus the flame's tint. Block items carry their
     * state's emission; the few luminous non-block items mirror
     * {@code RtEntityCollector.itemSpriteEmission}'s well-known list. */
    private record HeldLight(int level, float r, float g, float b) {
        static final HeldLight NONE = new HeldLight(0, 0.0f, 0.0f, 0.0f);
    }

    private static HeldLight heldLight(ItemStack stack) {
        if (stack.isEmpty()) {
            return HeldLight.NONE;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int level;
        if (stack.getItem() instanceof BlockItem blockItem) {
            level = blockItem.getBlock().defaultBlockState().getLightEmission();
        } else if (path.contains("lava")) {
            level = 15; // lava bucket
        } else if (path.contains("blaze_rod")) {
            level = 10;
        } else {
            level = 0;
        }
        if (level <= 0) {
            return HeldLight.NONE;
        }
        float[] tint = heldLightTint(path);
        return new HeldLight(level, tint[0], tint[1], tint[2]);
    }

    /** Ordered substring matching: soul/redstone variants before the generic torch check, sea lantern
     * before lantern, froglight variants before the ochre catch-all. */
    private static float[] heldLightTint(String path) {
        if (path.contains("soul_torch") || path.contains("soul_lantern")
                || path.contains("soul_campfire")) {
            return TINT_SOUL;
        }
        if (path.contains("redstone_torch")) {
            return TINT_REDSTONE;
        }
        if (path.contains("sea_lantern")) {
            return TINT_AQUA;
        }
        if (path.contains("sea_pickle")) {
            return TINT_PICKLE;
        }
        if (path.contains("torch") || path.contains("lantern") || path.contains("campfire")) {
            return TINT_TORCH;
        }
        if (path.contains("lava") || path.contains("magma")) {
            return TINT_LAVA;
        }
        if (path.contains("glowstone")) {
            return TINT_GLOWSTONE;
        }
        if (path.contains("shroomlight")) {
            return TINT_SHROOMLIGHT;
        }
        if (path.contains("verdant_froglight")) {
            return TINT_VERDANT;
        }
        if (path.contains("pearlescent_froglight")) {
            return TINT_PEARLESCENT;
        }
        if (path.contains("froglight")) { // ochre
            return TINT_OCHRE;
        }
        if (path.contains("end_rod")) {
            return TINT_END_ROD;
        }
        if (path.contains("beacon")) {
            return TINT_BEACON;
        }
        if (path.contains("crying_obsidian")) {
            return TINT_CRYING;
        }
        if (path.contains("redstone_lamp") || path.contains("jack_o_lantern")) {
            return TINT_AMBER;
        }
        if (path.contains("blaze_rod")) {
            return TINT_BLAZE;
        }
        return TINT_DEFAULT;
    }

    /**
     * Map the client level's dimension onto the shader's sky model. Uses {@code level.dimension()} —
     * the dimension's {@code ResourceKey}, which is what the server actually tells the client it is in —
     * rather than sniffing dimension type flags, so a datapack dimension that merely reuses the Nether's
     * or the End's type still renders as the Overworld unless it really is that dimension.
     */
    private static int dimensionId(ClientLevel level) {
        if (level == null) {
            return DIMENSION_OVERWORLD;
        }
        var dimension = level.dimension();
        if (Level.NETHER.equals(dimension)) {
            return DIMENSION_NETHER;
        }
        if (Level.END.equals(dimension)) {
            return DIMENSION_END;
        }
        return DIMENSION_OVERWORLD;
    }

    /**
     * Derive the celestial light from Minecraft's time of day as typed values for {@link WorldPushData}.
     * Celestial angles come from the camera's {@link EnvironmentAttributeProbe} (partial-tick
     * interpolated). {@code caustica.rt.sunNoonSouthDeg} tilts the east-west arc toward south (+Z) at
     * noon.
     *
     * <p>{@code weather} attenuates the resulting radiance, and a dimension with no celestial cycle
     * (Nether, End) zeroes it outright: neither has a sun or a moon, so a directional NEE light there
     * would be light arriving from nothing. The raygen skips the whole NEE block — shadow ray included —
     * when the radiance is zero, so those dimensions also stop paying for a light they do not have.
     */
    private EnvironmentParameters.Sky skyPush(int dimension, EnvironmentParameters.Weather weather,
                                              EnvironmentParameters.Time time) {
        float sunX, sunY, sunZ, dayFactor, lx, ly, lz, rr, rg, rb, lightRadius;
        float moonX, moonY, moonZ, moonPhase, starAngle, starBrightness;
        Minecraft mc = Minecraft.getInstance();
        float partial = time.partialTick();
        var probe = mc.gameRenderer.mainCamera().attributeProbe();
        float sunAngle = probe.getValue(EnvironmentAttributes.SUN_ANGLE, partial) * (float) (Math.PI / 180.0);
        float moonAngle = probe.getValue(EnvironmentAttributes.MOON_ANGLE, partial) * (float) (Math.PI / 180.0);
        float sunNoon = Mth.cos(sunAngle);
        sunX = -Mth.sin(sunAngle); sunY = sunNoonY() * sunNoon; sunZ = sunNoonZ() * sunNoon;
        float moonNoon = Mth.cos(moonAngle);
        moonX = -Mth.sin(moonAngle); moonY = sunNoonY() * moonNoon; moonZ = sunNoonZ() * moonNoon;
        moonPhase = probe.getValue(EnvironmentAttributes.MOON_PHASE, partial).index(); // 0 full .. 4 new
        // Stars: use Minecraft's actual celestial rotation + brightness (the same values vanilla's
        // SkyRenderer uses), so the starfield wheels about the celestial pole tied to world time and
        // fades in/out at dusk/dawn exactly like vanilla. STAR_ANGLE is in degrees -> radians.
        starAngle = probe.getValue(EnvironmentAttributes.STAR_ANGLE, partial) * (float) (Math.PI / 180.0);
        starBrightness = probe.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partial);
        dayFactor = smoothstep(-0.08f, 0.10f, sunY);
        float[] trans = new float[3];
        if (sunY > -0.05f) {
            // Sun stays the NEE light through the whole sunset: its colour/intensity is the atmosphere's
            // own transmittance (same Rayleigh+Mie+ozone march as the sky shader — see
            // atmosphereTransmittance), so it whitens overhead and reddens+dims into the horizon on
            // exactly the curve the visible sky follows. The old hand-tuned warmth ramp switched to the
            // moon at sunY == 0 while the sun was still at ~16% strength, which read as a hard light pop
            // at sunset/sunrise; transmittance is already near zero at the horizon, and the short
            // smoothstep below carries the remainder to exactly zero before the moon takes over.
            atmosphereTransmittance(sunX, sunY, sunZ, trans);
            float fade = smoothstep(-0.05f, 0.005f, sunY);
            float sunPeak = 21.0f;
            lx = sunX; ly = sunY; lz = sunZ;
            rr = sunPeak * trans[0] * fade;
            rg = sunPeak * trans[1] * fade;
            rb = sunPeak * trans[2] * fade;
            lightRadius = CausticaConfig.Rt.Composite.SUN_ANGULAR_RADIUS.value();
        } else {
            // Moon: dim cool light, ramping up from zero at the sun→moon handoff (sunY = -0.05, where
            // the sun fade also reaches zero) so the switch is invisible. Scaled by the lit fraction so
            // a new moon gives near-zero moonlight, and tinted by the same transmittance so a low moon
            // is warm amber, silver once high (or zero while it is below the horizon).
            atmosphereTransmittance(moonX, moonY, moonZ, trans);
            float moonStrength = smoothstep(0.04f, 0.22f, -sunY);
            float litFraction = moonLitFraction(moonPhase); // 0 new .. 1 full
            float moonPeak = 0.20f * (0.15f + 0.85f * litFraction);
            lx = moonX; ly = moonY; lz = moonZ;
            rr = 0.30f * moonPeak * moonStrength * trans[0];
            rg = 0.36f * moonPeak * moonStrength * trans[1];
            rb = 0.55f * moonPeak * moonStrength * trans[2];
            lightRadius = CausticaConfig.Rt.Composite.MOON_ANGULAR_RADIUS.value();
        }
        // Weather attenuation, applied to whichever body is currently the NEE light. Overcast does not
        // just dim the sun, it replaces it with a diffuse source — the sky term in world.rmiss carries
        // that half — so the directional component drops further than the sky does (see weatherState).
        float lightAttenuation = weather.lightAttenuation();
        // No celestial cycle in the Nether/End: no sun, no moon, no directional light, no stars.
        if (dimension != DIMENSION_OVERWORLD) {
            lightAttenuation = 0f;
            starBrightness = 0f;
        }
        rr *= lightAttenuation;
        rg *= lightAttenuation;
        rb *= lightAttenuation;
        // Stars are behind the cloud deck during rain; the sky shader fades them out on the same ramp.
        starBrightness *= 1.0f - weather.rain();

        WorldTraceResources.CelestialUv uv = worldTraceResources.celestialUv(moonPhase);
        return new EnvironmentParameters.Sky(
                new Float4(sunX, sunY, sunZ, dayFactor),
                new Float4(lx, ly, lz, lightRadius),
                new Float4(rr, rg, rb, starBrightness),
                new Float4(moonX, moonY, moonZ, moonPhase),
                new Float4(0f, celestialAxisY(), celestialAxisZ(), starAngle),
                uv.sun(),
                uv.moon());
    }

    /** Hermite smoothstep matching GLSL semantics (0 below edge0, 1 above edge1). */
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Minecraft moon phase index to illuminated fraction: phase 0 (full moon) -> 1.0, phase 4 (new moon) -> 0.0.
     */
    static float moonLitFraction(float moonPhase) {
        return Math.abs(moonPhase - 4.0f) / 4.0f;
    }


    /**
     * RGB transmittance from the camera to space along {@code dir} — a verbatim port of
     * {@code world.rmiss}'s {@code transmittanceToSpace} (Rayleigh + Mie + ozone optical depth, 8-step
     * march from 2 km altitude; constants must stay in lock-step with the shader). This is what colours
     * the NEE sun/moonlight: because the sky shader tints its visible discs with the identical function,
     * the light on terrain and the sky's sunset can never disagree. A direction below the geometric
     * horizon accumulates enormous optical depth, so the result rolls to zero smoothly on its own —
     * no explicit planet-shadow test needed.
     */
    private static void atmosphereTransmittance(float dx, float dy, float dz, float[] out) {
        // Final single sky: vibrant and blue (Minecraft RTX style) - aggressive vivid
        final double planetR = 6371000.0, atmosR = 6471000.0;
        final double[] rayBeta = {3.2e-6, 8.5e-6, 24.5e-6}; // pure saturated vibrant blue
        final double mieBeta = 6.0e-6 * 1.1; // RADICALLY lowered to kill gray haze (was 38/24/18)
        final double[] ozoneBeta = {0.650e-6, 1.881e-6, 0.085e-6};
        final double oy = planetR + 2000.0;
        double b = oy * dy;
        double tEnd = -b + Math.sqrt(Math.max(b * b - (oy * oy - atmosR * atmosR), 0.0));
        double seg = tEnd / 8.0;
        double odR = 0.0, odM = 0.0, odO = 0.0;
        for (int i = 0; i < 8; i++) {
            double t = seg * (i + 0.5);
            double px = dx * t, py = oy + dy * t, pz = dz * t;
            double h = Math.sqrt(px * px + py * py + pz * pz) - planetR;
            odR += Math.exp(-h / 9000.0) * seg;
            odM += Math.exp(-h / 900.0) * seg;
            odO += Math.max(0.0, 1.0 - Math.abs(h - 25000.0) / 15000.0) * seg;
        }
        for (int i = 0; i < 3; i++) {
            out[i] = (float) Math.exp(-(rayBeta[i] * odR + mieBeta * odM + ozoneBeta[i] * odO));
        }
    }

    public void destroy() {
        sharc.destroy(RtContext.currentOrNull());
        // Unconditional: backend availability reflects the CURRENT toggles, and the denoiser toggle can
        // have been turned off after a feature was already created. destroy() is a no-op when nothing
        // was ever allocated, so asking it every time is what guarantees the feature is released.
        dlssRrBackend.destroy();
        // Tear down the NRD integration (wraps the Vulkan device via NRI) before its borrowed images.
        nrdBackend.destroy();
        svgfBackend.destroy();
        frameGenerationResources.destroyAfterDeviceIdle();
        postProcessing.destroyImages();
        RtWorldOverlay.INSTANCE.destroy(); // overlay features/pipelines/scratch live on the same device lifetime
        traceFrameResources.release();
        restirSystem.destroy();
        postProcessing.destroyPipelineAndExposure();
        postProcessing.destroyPresentationAfterDeviceIdle();
        worldTraceResources.destroy();
    }

    private static long vkImage(GpuTexture texture) {
        if (texture instanceof VulkanGpuTexture vulkanTexture) {
            return vulkanTexture.vkImage();
        }
        throw new IllegalStateException("cannot resolve VkImage for " + texture);
    }

    public boolean isHdrPresentActive() {
        return postProcessing.isHdrPresentActive();
    }

    public long hdrBackbufferView() {
        return postProcessing.hdrBackbufferView();
    }

    public long hdrBackbufferImage() {
        return postProcessing.hdrBackbufferImage();
    }

    public void presentHdr(VulkanCommandEncoder encoder, long swapchainImage, int swapW, int swapH,
                           long acquireSemaphore, long presentSemaphore) {
        postProcessing.presentHdr(encoder, swapchainImage, swapW, swapH, acquireSemaphore, presentSemaphore,
                FrameGenerationResources.hudlessNeeded()
                        ? frameGenerationResources::captureHdrHudless
                        : (ctx, command, stack, source) -> { });
    }

    public boolean isPqSdrPresentActive() {
        return postProcessing.isPqSdrPresentActive();
    }

    public boolean presentSdrToPq(VulkanCommandEncoder encoder, long swapchainImage, int swapW, int swapH,
                                  long sdrMainView, long acquireSemaphore, long presentSemaphore) {
        return postProcessing.presentSdrToPq(encoder, swapchainImage, swapW, swapH, sdrMainView,
                acquireSemaphore, presentSemaphore, failed);
    }

    /** Capture the pre-UI SDR frame at the existing renderer seam. */
    public void captureFgHudless(RenderTarget main) {
        frameGenerationResources.captureHudless(main);
    }

    public static int fgGeneratedCount() {
        return FrameGenerationResources.generatedCount();
    }

    /** Route frame-generation work to its semantic resource owner. */
    public RtImage fgInterpolate(VulkanCommandEncoder encoder, long backbufferView, long backbufferImage,
                                 int swapW, int swapH, int index, int count, boolean hdrBackbuffer) {
        return frameGenerationResources.interpolate(encoder, backbufferView, backbufferImage,
                swapW, swapH, index, count, hdrBackbuffer,
                new FrameGenerationResources.FrameData(
                        frameViews(), mvCurProjView, mvPrevProjView, frameProjection, frameViewRotation,
                        camX, camY, camZ, frameCounter, !failed && frameCaptured),
                postProcessing);
    }

}
