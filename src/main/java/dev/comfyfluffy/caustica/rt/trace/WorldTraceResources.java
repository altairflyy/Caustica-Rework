package dev.comfyfluffy.caustica.rt.trace;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.RtDeviceBringup;
import dev.comfyfluffy.caustica.rt.RtGpuExecutor;
import dev.comfyfluffy.caustica.rt.accel.RtBuffer;
import dev.comfyfluffy.caustica.rt.entity.RtEntityTextures;
import dev.comfyfluffy.caustica.rt.gen.WorldPushConstantsData;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float4;
import dev.comfyfluffy.caustica.rt.material.RtBlockMaterials;
import dev.comfyfluffy.caustica.rt.material.RtEmissionSemantics;
import dev.comfyfluffy.caustica.rt.material.RtMaterialOverrides;
import dev.comfyfluffy.caustica.rt.material.RtMaterialRegistry;
import dev.comfyfluffy.caustica.rt.pipeline.RtPipeline;
import dev.comfyfluffy.caustica.rt.terrain.RtTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * Semantic owner of the world ray-tracing pipeline, its atlas/material bindings and the
 * host-visible world-push ring. Frame-sized trace images are borrowed through immutable views.
 */
public final class WorldTraceResources {
    private static final int PUSH_RING_SIZE = 6;
    private static final Identifier SUN_ID = Identifier.withDefaultNamespace("sun");
    private static final Identifier[] MOON_IDS = createMoonIds();

    private final int worldPushBufferSize;
    private final int guideCount;

    private RtPipeline worldPipeline;
    private long atlasSampler;
    private PushSlot[] pushRing;
    private int pushSlot;

    private volatile boolean reloadRebindRequested;
    private long boundBlockAlbedoAtlasHandle;
    private int bindlessTextureCapacity;
    private boolean materialBindingsReady;
    private boolean materialEpochTraceGate;

    private long celestialUvAtlasHandle;
    private int celestialUvMoonPhase = -1;
    private float sunU0;
    private float sunV0;
    private float sunU1 = 1f;
    private float sunV1 = 1f;
    private float moonU0;
    private float moonV0;
    private float moonU1 = 1f;
    private float moonV1 = 1f;

    public WorldTraceResources(int worldPushBufferSize, int guideCount) {
        if (worldPushBufferSize <= 0) {
            throw new IllegalArgumentException("worldPushBufferSize must be positive");
        }
        if (guideCount <= 0) {
            throw new IllegalArgumentException("guideCount must be positive");
        }
        this.worldPushBufferSize = worldPushBufferSize;
        this.guideCount = guideCount;
    }

    /** Bring resources up ahead of terrain capture, without completing a pending reload. */
    public void ensureAheadOfFrame(RtContext ctx, FrameViews frameViews) {
        if (worldPipeline != null || reloadRebindRequested) {
            return;
        }
        if (Minecraft.getInstance().level == null || blockAlbedoAtlasView() == 0L) {
            return;
        }
        ensureWorld(ctx, frameViews);
    }

    /** Apply bindless-shape changes and ensure the active world pipeline for a render frame. */
    public void ensureForFrame(RtContext ctx, FrameViews frameViews) {
        refreshPipelineShapeIfNeeded(ctx);
        ensureWorld(ctx, frameViews);
    }

    /** Whether a pending reload has published a fresh non-zero block-atlas view. */
    public boolean reloadReady() {
        if (!reloadRebindRequested) {
            return true;
        }
        long atlas = blockAlbedoAtlasView();
        return atlas != 0L && atlas != boundBlockAlbedoAtlasHandle;
    }

    /** Preserve the legacy vanilla fallback gate while world resources converge. */
    public boolean requiresVanillaFallback() {
        if (worldPipeline == null || !materialBindingsReady || materialEpochTraceGate) {
            return true;
        }
        if (RtEntityTextures.maxTextures() > bindlessTextureCapacity) {
            return true;
        }
        return !reloadReady();
    }

    /** Consume the one-frame material epoch gate after creation/rebinding. */
    public boolean consumeMaterialEpochTraceGate() {
        if (!materialEpochTraceGate) {
            return false;
        }
        materialEpochTraceGate = false;
        return true;
    }

    /** Refresh material descriptors if an existing pipeline has lost binding readiness. */
    public void refreshMaterialBindingsIfNeeded(RtContext ctx) {
        if (worldPipeline != null && !reloadRebindRequested && !materialBindingsReady) {
            bindWorldTextures(ctx);
        }
    }

    /**
     * Invalidate descriptor users at reload start. The callback preserves the legacy entity-reset
     * position before the existing idle seam and pipeline/material destruction.
     */
    public void onResourceReload(Runnable beforeQuiescence) {
        reloadRebindRequested = true;
        materialBindingsReady = false;
        setCelestialUvAtlas(0L);
        beforeQuiescence.run();
        RtContext ctx = RtContext.currentOrNull();
        if (ctx != null) {
            ctx.waitIdle();
            destroyPipeline();
            RtMaterialRegistry.INSTANCE.destroy();
        }
    }

    /** Rebind frame-sized output and guide views without taking ownership of them. */
    public void bindFrameViews(FrameViews views) {
        if (worldPipeline == null || views == null || views.outputView() == 0L) {
            return;
        }
        long[] guideViews = views.guideViews();
        if (guideViews.length != guideCount) {
            throw new IllegalArgumentException("Expected " + guideCount + " guide views, got " + guideViews.length);
        }
        worldPipeline.setStorageImage(views.outputView());
        for (int i = 0; i < guideViews.length; i++) {
            worldPipeline.setExtraStorageImage(i, guideViews[i]);
        }
    }

    /** Select the next push slot and await its exact last graphics use before host reuse. */
    public RtBuffer acquirePushBuffer(RtGpuExecutor.GraphicsUse graphicsUse,
                                      RtGpuExecutor.GraphicsUseWaiter graphicsUseWaiter) {
        pushSlot = nextPushSlot(pushSlot);
        PushSlot selected = pushRing[pushSlot];
        graphicsUseWaiter.await(selected.graphicsUse);
        selected.graphicsUse.mark(graphicsUse);
        return selected.buffer;
    }

    public void uploadPendingEntityTextures(RtContext ctx) {
        RtEntityTextures.INSTANCE.uploadPending(worldPipeline, atlasSampler(ctx));
    }

    public void bindTlas(long tlas, RtGpuExecutor.GraphicsUse graphicsUse,
                         RtGpuExecutor.GraphicsUseWaiter graphicsUseWaiter) {
        worldPipeline.setTlas(tlas, graphicsUse, graphicsUseWaiter);
    }

    public void trace(VkCommandBuffer command, int width, int height, ByteBuffer pushConstants,
                      int raygenIndex) {
        worldPipeline.trace(command, width, height, pushConstants, raygenIndex);
    }

    public CelestialUv celestialUv(float moonPhaseIndex) {
        if (celestialUvAtlasHandle == 0L) {
            setCelestialUvAtlas(celestialsAtlasView());
        }
        int phase = Math.clamp((int) moonPhaseIndex, 0, MOON_IDS.length - 1);
        if (phase != celestialUvMoonPhase) {
            refreshCelestialUvCache(phase);
        }
        return new CelestialUv(
                new Float4(sunU0, sunV0, sunU1, sunV1),
                new Float4(moonU0, moonV0, moonU1, moonV1));
    }

    /** Nominal shutdown; the caller preserves the existing device-lifetime shutdown sequence. */
    public void destroy() {
        destroyPipeline();
        materialEpochTraceGate = false;
        RtMaterialRegistry.INSTANCE.destroy();
        if (pushRing != null) {
            for (PushSlot slot : pushRing) {
                if (slot != null) {
                    slot.buffer.destroy();
                }
            }
            pushRing = null;
        }
        if (atlasSampler != 0L) {
            RtContext ctx = RtContext.currentOrNull();
            if (ctx != null) {
                VK10.vkDestroySampler(ctx.vk(), atlasSampler, null);
            }
            atlasSampler = 0L;
        }
    }

    static int nextPushSlot(int current) {
        return (current + 1) % PUSH_RING_SIZE;
    }

    private void ensureWorld(RtContext ctx, FrameViews frameViews) {
        if (worldPipeline != null) {
            return;
        }
        bindlessTextureCapacity = RtEntityTextures.maxTextures();
        worldPipeline = RtPipeline.create(ctx, new String[]{
                        RtDeviceBringup.worldPrimaryRaygenShader(),
                        RtDeviceBringup.worldRaygenShader()},
                new String[]{"world.rmiss.spv", "world_guide.rmiss.spv"},
                "world.rchit.spv", "world.rahit.spv",
                WorldPushConstantsData.BYTE_SIZE, true, guideCount, bindlessTextureCapacity, true);
        if (pushRing == null) {
            pushRing = new PushSlot[PUSH_RING_SIZE];
            for (int i = 0; i < PUSH_RING_SIZE; i++) {
                pushRing[i] = new PushSlot(ctx.createBuffer(worldPushBufferSize,
                        VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true, "rt world push " + i));
            }
        }
        bindFrameViews(frameViews);
        bindWorldTextures(ctx);
        reloadRebindRequested = false;
    }

    private void refreshPipelineShapeIfNeeded(RtContext ctx) {
        if (worldPipeline == null || reloadRebindRequested) {
            return;
        }
        int desiredBindlessCapacity = RtEntityTextures.maxTextures();
        if (desiredBindlessCapacity <= bindlessTextureCapacity) {
            return;
        }
        ctx.waitIdle();
        destroyPipeline();
    }

    private void bindWorldTextures(RtContext ctx) {
        long sampler = atlasSampler(ctx);
        long atlasView = blockAlbedoAtlasView();
        boundBlockAlbedoAtlasHandle = atlasView;
        worldPipeline.setBlockAlbedoAtlas(atlasView, sampler);
        RtBlockMaterials.INSTANCE.reset();
        RtMaterialOverrides materialOverrides = RtMaterialOverrides.load();
        RtEmissionSemantics emissionSemantics = RtEmissionSemantics.analyze();
        RtBlockMaterials.INSTANCE.prepareAll(ctx, bindlessTextureCapacity, emissionSemantics, materialOverrides);
        RtEntityTextures.INSTANCE.reset(bindlessTextureCapacity);
        worldPipeline.setEntityAlbedoTexture(0, atlasView, sampler);
        RtBlockMaterials.INSTANCE.bindPages(worldPipeline, sampler);
        RtMaterialRegistry.INSTANCE.rebuild(ctx, RtBlockMaterials.INSTANCE, materialOverrides);
        materialBindingsReady = true;
        long celestialView = celestialsAtlasView();
        if (worldPipeline.hasSkyAtlas()) {
            worldPipeline.setSkyAtlas(celestialView != 0L ? celestialView : atlasView, sampler);
        }
        setCelestialUvAtlas(celestialView);
        RtTerrain.requestFullClear();
        materialEpochTraceGate = true;
    }

    private void destroyPipeline() {
        if (worldPipeline != null) {
            worldPipeline.destroy();
            worldPipeline = null;
        }
        bindlessTextureCapacity = 0;
        materialBindingsReady = false;
    }

    private long atlasSampler(RtContext ctx) {
        if (atlasSampler == 0L) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                        .magFilter(VK10.VK_FILTER_NEAREST).minFilter(VK10.VK_FILTER_NEAREST)
                        .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR)
                        .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                        .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                        .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                        .minLod(0f).maxLod(16f);
                LongBuffer pointer = stack.mallocLong(1);
                if (VK10.vkCreateSampler(ctx.vk(), createInfo, null, pointer) != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateSampler(block atlas) failed");
                }
                atlasSampler = pointer.get(0);
                RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_SAMPLER, atlasSampler, "block atlas sampler");
            }
        }
        return atlasSampler;
    }

    private void setCelestialUvAtlas(long atlasHandle) {
        if (celestialUvAtlasHandle == atlasHandle) {
            return;
        }
        celestialUvAtlasHandle = atlasHandle;
        celestialUvMoonPhase = -1;
        sunU0 = 0f;
        sunV0 = 0f;
        sunU1 = 1f;
        sunV1 = 1f;
        moonU0 = 0f;
        moonV0 = 0f;
        moonU1 = 1f;
        moonV1 = 1f;
    }

    private void refreshCelestialUvCache(int moonPhase) {
        sunU0 = 0f;
        sunV0 = 0f;
        sunU1 = 1f;
        sunV1 = 1f;
        moonU0 = 0f;
        moonV0 = 0f;
        moonU1 = 1f;
        moonV1 = 1f;
        try {
            if (celestialUvAtlasHandle != 0L) {
                TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
                TextureAtlasSprite sun = atlas.getSprite(SUN_ID);
                sunU0 = sun.getU0();
                sunV0 = sun.getV0();
                sunU1 = sun.getU1();
                sunV1 = sun.getV1();
                TextureAtlasSprite moon = atlas.getSprite(MOON_IDS[moonPhase]);
                moonU0 = moon.getU0();
                moonV0 = moon.getV0();
                moonU1 = moon.getU1();
                moonV1 = moon.getV1();
            }
        } catch (Exception ignored) {
            // Atlas not ready: preserve the full-range fallback UVs.
        }
        celestialUvMoonPhase = moonPhase;
    }

    private static Identifier[] createMoonIds() {
        MoonPhase[] phases = MoonPhase.values();
        Identifier[] ids = new Identifier[phases.length];
        for (int i = 0; i < phases.length; i++) {
            ids[i] = Identifier.withDefaultNamespace("moon/" + phases[i].getSerializedName());
        }
        return ids;
    }

    private static long blockAlbedoAtlasView() {
        GpuTextureView view = Minecraft.getInstance().getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        return vkImageView(view);
    }

    private static long celestialsAtlasView() {
        try {
            GpuTextureView view = Minecraft.getInstance().getAtlasManager()
                    .getAtlasOrThrow(AtlasIds.CELESTIALS).getTextureView();
            return vkImageView(view);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long vkImageView(GpuTextureView view) {
        if (view instanceof VulkanGpuTextureView vulkanView) {
            return vulkanView.vkImageView();
        }
        throw new IllegalStateException("cannot resolve VkImageView for " + view);
    }

    private static final class PushSlot {
        private final RtBuffer buffer;
        private final RtGpuExecutor.TrackedGraphicsUse graphicsUse = new RtGpuExecutor.TrackedGraphicsUse();

        private PushSlot(RtBuffer buffer) {
            this.buffer = buffer;
        }
    }

    /** Immutable borrowed frame-image handles. The owner retains no image ownership. */
    public record FrameViews(long outputView, long[] guideViews) {
        public FrameViews {
            guideViews = guideViews == null ? new long[0] : guideViews.clone();
        }

        @Override
        public long[] guideViews() {
            return guideViews.clone();
        }
    }

    public record CelestialUv(Float4 sun, Float4 moon) {
    }
}
