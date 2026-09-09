package dev.comfyfluffy.caustica.rt.post;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.RtUiOverlay;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.gpu.FrameTailRetirement;
import dev.comfyfluffy.caustica.rt.pipeline.RtDisplayPipeline;
import dev.comfyfluffy.caustica.rt.pipeline.RtExposure;
import dev.comfyfluffy.caustica.rt.pipeline.RtHdrCompositePipeline;
import dev.comfyfluffy.caustica.rt.pipeline.RtSdrPresentPipeline;
import dev.comfyfluffy.caustica.rt.graph.PostBarrierPlan;
import dev.comfyfluffy.caustica.rt.graph.PostImageBarriers;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.util.Objects;

/**
 * Owner of exposure, display mapping, SDR/HDR targets and presentation resources.
 * The caller keeps only the legacy orchestration boundaries between this owner and the other domains.
 */
public final class PostProcessing implements GeneratedFrameUiComposer {
    private final FrameTailRetirement retirement;
    private RtDisplayPipeline displayPipeline;
    private final RtExposure exposure = new RtExposure();
    private RtImage displayImage;
    private RtImage hdrDisplayImage;
    private RtHdrCompositePipeline hdrCompositePipeline;
    private long hdrUiSampler;
    private RtSdrPresentPipeline sdrPresentPipeline;
    private RtImage sdrPresentImage;
    private boolean hdrWrittenThisFrame;
    private int loggedPostBarrierMode = -1;

    public PostProcessing(FrameTailRetirement retirement) {
        this.retirement = Objects.requireNonNull(retirement, "retirement");
    }

    public void beginFrame() { hdrWrittenThisFrame = false; }

    public void ensurePipeline(RtContext ctx) {
        if (displayPipeline == null) displayPipeline = RtDisplayPipeline.create(ctx);
    }

    public void ensureExposure(RtContext ctx) { exposure.ensureResources(ctx); }
    public boolean exposureReady() { return exposure.ready(); }
    public boolean imagesReady() { return displayImage != null && hdrDisplayImage != null; }
    public RtImage hdrImage() { return hdrDisplayImage; }

    /** Caller has already waited for legacy resize safety. Preserve failure state/order. */
    public void releaseImagesForResize() {
        if (displayImage != null) displayImage.destroy();
        if (hdrDisplayImage != null) hdrDisplayImage.destroy();
    }

    public void createImages(RtContext ctx, int width, int height) {
        displayImage = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R8G8B8A8_UNORM, "RT display image " + width + "x" + height);
        // PQ-encoded ([0,1], ST.2084) HDR display image, written in parallel by display.comp when HDR mode is active.
        hdrDisplayImage = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R16G16B16A16_SFLOAT, "RT HDR display image " + width + "x" + height);
    }

    public void bind(RtImage rrOutput) {
        displayPipeline.setImages(displayImage.view, rrOutput.view, exposure.image().view, hdrDisplayImage.view);
    }

    /** Signal display completion at the original boundary, even if the later copy fails. */
    public void record(RtContext ctx, VkCommandBuffer cmd, MemoryStack stack, RtImage rrOutput,
                       int displayW, int displayH, long dstImage, boolean postHdr) {
        PostBarrierPlan postPlan;
        // Auto-exposure meters rrOutput (the post-RR, denoised/converged image), not the raw
        // pre-RR trace: RR has no notion of exposure (DLSS-RR Integration Guide §3.7 — ignore
        // exposure/auto-exposure/sharpness entirely for RR), so this is purely our own metering
        // choice, independent of RR's pipeline placement. Metering the noisy pre-RR buffer made
        // the histogram's log-luminance average biased by Monte-Carlo noise (Jensen's inequality
        // on the concave log()), so the computed exposure drifted with SPP; rrOutput is stable
        // regardless of SPP, keeping exposure consistent.
        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, "exposure");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.exposure")) {
            postPlan = exposure.record(ctx, cmd, stack, rrOutput, postHdr);
        }
        PostImageBarriers.before(cmd, stack, postPlan, PostBarrierPlan.DISPLAY);

        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, "map RT to display");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.displayMap")) {
            displayPipeline.dispatch(cmd, displayW, displayH, postHdr,
                    CausticaConfig.Rt.Hdr.paperWhiteNits(), CausticaConfig.Rt.Hdr.headroom(),
                    CausticaConfig.Rt.Tonemapping.operatorIndex(),
                    CausticaConfig.Rt.Tonemapping.EXPOSURE_EV.value(),
                    CausticaConfig.Rt.Tonemapping.GAMMA.value(),
                    CausticaConfig.Rt.Tonemapping.SATURATION.value(),
                    CausticaConfig.Rt.Tonemapping.CONTRAST.value());
        }
        hdrWrittenThisFrame = postHdr;
        PostImageBarriers.before(cmd, stack, postPlan, PostBarrierPlan.COPY);

        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, "copy composite to main target");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.copyOutput")) {
            VK10.vkCmdCopyImage(cmd, displayImage.image, VK10.VK_IMAGE_LAYOUT_GENERAL,
                    dstImage, VK10.VK_IMAGE_LAYOUT_GENERAL, copyRegion(stack, displayW, displayH));
        }
        PostImageBarriers.before(cmd, stack, postPlan, PostBarrierPlan.EXPORT);
        int postMode = 4 | (postPlan.automaticExposure() ? 2 : 0) | (postHdr ? 1 : 0);
        if (loggedPostBarrierMode != postMode) {
            CausticaMod.LOGGER.info("AER-083 post barriers: path={}, exposure={}, hdr={}, scope=legacy-conservative",
                    "generated",
                    postPlan.automaticExposure() ? "auto" : "manual", postHdr);
            loggedPostBarrierMode = postMode;
        }
    }

    public boolean isHdrPresentActive() {
        return CausticaConfig.Rt.Hdr.enabled() && hdrWrittenThisFrame && hdrDisplayImage != null;
    }

    public boolean isPqSdrPresentActive() {
        return CausticaConfig.Rt.Hdr.enabled() && !isHdrPresentActive();
    }

    public long hdrBackbufferView() { return hdrDisplayImage != null ? hdrDisplayImage.view : 0L; }
    public long hdrBackbufferImage() { return hdrDisplayImage != null ? hdrDisplayImage.image : 0L; }

    public void presentHdr(VulkanCommandEncoder encoder, long swapchainImage, int swapW, int swapH,
                           long acquireSemaphore, long presentSemaphore, HdrHudlessCapture capture) {
        RtImage source = hdrDisplayImage;
        int copyW = Math.min(swapW, source.width);
        int copyH = Math.min(swapH, source.height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer command = encoder.allocateAndBeginTransientCommandBuffer();
            capture.capture(RtContext.get(), command, stack, source);

            long overlayView = RtUiOverlay.populatedThisFrame() ? RtUiOverlay.overlayColorView() : 0L;
            if (overlayView != 0L) {
                ensureHdrUiResources();
                if (hdrCompositePipeline != null) {
                    VkMemoryBarrier2.Buffer pre = VkMemoryBarrier2.calloc(1, stack).sType$Default();
                    pre.get(0).srcStageMask(65536L).srcAccessMask(65536L)
                            .dstStageMask(2048L).dstAccessMask(98304L);
                    KHRSynchronization2.vkCmdPipelineBarrier2KHR(command,
                            VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(pre));
                    composeHdrGenerated(command, source, overlayView, source.width, source.height);
                }
                RtUiOverlay.markConsumed();
            }
            recordPresentBlit(encoder, command, stack, source.image, VK10.VK_IMAGE_LAYOUT_GENERAL,
                    swapchainImage, copyW, copyH, acquireSemaphore, presentSemaphore, "hdr present");
        }
    }

    public boolean presentSdrToPq(VulkanCommandEncoder encoder, long swapchainImage, int swapW, int swapH,
                                  long sdrMainView, long acquireSemaphore, long presentSemaphore,
                                  boolean rendererFailed) {
        if (sdrMainView == 0L || rendererFailed) return false;
        RtContext ctx = RtContext.get();
        if (ctx == null || uiSampler(ctx) == 0L) return false;
        if (sdrPresentPipeline == null) sdrPresentPipeline = RtSdrPresentPipeline.create(ctx);
        if (sdrPresentImage == null || sdrPresentImage.width != swapW || sdrPresentImage.height != swapH) {
            RtImage old = sdrPresentImage;
            sdrPresentImage = ctx.createStorageImage(swapW, swapH, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    "RT SDR->PQ present image " + swapW + "x" + swapH);
            if (old != null) retirement.retire(old::destroy);
        }
        RtImage destination = sdrPresentImage;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer command = encoder.allocateAndBeginTransientCommandBuffer();
            VkMemoryBarrier2.Buffer pre = VkMemoryBarrier2.calloc(1, stack).sType$Default();
            pre.get(0).srcStageMask(65536L).srcAccessMask(65536L)
                    .dstStageMask(2048L).dstAccessMask(98304L);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(command,
                    VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(pre));
            sdrPresentPipeline.setImages(destination.view, sdrMainView, hdrUiSampler);
            sdrPresentPipeline.dispatch(command, destination.width, destination.height,
                    CausticaConfig.Rt.Hdr.paperWhiteNits());
            recordPresentBlit(encoder, command, stack, destination.image, VK10.VK_IMAGE_LAYOUT_GENERAL,
                    swapchainImage, Math.min(swapW, destination.width), Math.min(swapH, destination.height),
                    acquireSemaphore, presentSemaphore, "sdr present");
        }
        return true;
    }

    private static void recordPresentBlit(VulkanCommandEncoder encoder, VkCommandBuffer command,
                                          MemoryStack stack, long sourceImage, int sourceLayout,
                                          long swapchainImage, int width, int height,
                                          long acquireSemaphore, long presentSemaphore,
                                          String operation) {
        VkImageMemoryBarrier2.Buffer toDestination = VkImageMemoryBarrier2.calloc(1, stack).sType$Default();
        toDestination.get(0).srcStageMask(0L).srcAccessMask(0L)
                .dstStageMask(4096L).dstAccessMask(4096L)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(-1).dstQueueFamilyIndex(-1).image(swapchainImage);
        toDestination.get(0).subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
        VkMemoryBarrier2.Buffer sourceVisibility = VkMemoryBarrier2.calloc(1, stack).sType$Default();
        sourceVisibility.get(0).srcStageMask(65536L).srcAccessMask(65536L)
                .dstStageMask(4096L).dstAccessMask(2048L);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(command,
                VkDependencyInfo.calloc(stack).sType$Default()
                        .pImageMemoryBarriers(toDestination).pMemoryBarriers(sourceVisibility));

        VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
        region.get(0).srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.get(0).dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.get(0).srcOffsets(1).set(width, height, 1);
        region.get(0).dstOffsets(0).set(0, height, 0);
        region.get(0).dstOffsets(1).set(width, 0, 1);
        VK10.vkCmdBlitImage(command, sourceImage, sourceLayout, swapchainImage,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region, VK10.VK_FILTER_NEAREST);

        VkImageMemoryBarrier2.Buffer toPresent = VkImageMemoryBarrier2.calloc(1, stack).sType$Default();
        toPresent.get(0).srcStageMask(4096L).srcAccessMask(4096L)
                .dstStageMask(65536L).dstAccessMask(0L)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL).newLayout(1000001002)
                .srcQueueFamilyIndex(-1).dstQueueFamilyIndex(-1).image(swapchainImage);
        toPresent.get(0).subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
        VkMemoryBarrier2.Buffer memory = VkMemoryBarrier2.calloc(1, stack).sType$Default();
        memory.get(0).srcStageMask(4096L).srcAccessMask(2048L)
                .dstStageMask(65536L).dstAccessMask(98304L);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(command,
                VkDependencyInfo.calloc(stack).sType$Default()
                        .pImageMemoryBarriers(toPresent).pMemoryBarriers(memory));
        if (VK10.vkEndCommandBuffer(command) != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkEndCommandBuffer(" + operation + ") failed");
        }
        encoder.waitSemaphore(acquireSemaphore, 0L, 65536L);
        encoder.execute(command);
        encoder.signalSemaphore(presentSemaphore, 0L, 4096L);
    }

    @Override
    public long uiSampler(RtContext ctx) {
        if (hdrUiSampler != 0L) return hdrUiSampler;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK10.VK_FILTER_NEAREST).minFilter(VK10.VK_FILTER_NEAREST)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
            var pointer = stack.mallocLong(1);
            if (VK10.vkCreateSampler(ctx.vk(), createInfo, null, pointer) != VK10.VK_SUCCESS) return 0L;
            hdrUiSampler = pointer.get(0);
            return hdrUiSampler;
        }
    }

    @Override
    public void composeHdrGenerated(VkCommandBuffer command, RtImage target, long overlayView,
                                    int width, int height) {
        ensureHdrUiResources();
        if (hdrCompositePipeline != null) {
            hdrCompositePipeline.setImages(target.view, overlayView, hdrUiSampler);
            hdrCompositePipeline.dispatch(command, width, height, CausticaConfig.Rt.Hdr.paperWhiteNits());
        }
    }

    private void ensureHdrUiResources() {
        if (hdrCompositePipeline != null) return;
        RtContext ctx = RtContext.get();
        if (ctx != null && uiSampler(ctx) != 0L) hdrCompositePipeline = RtHdrCompositePipeline.create(ctx);
    }

    @FunctionalInterface
    public interface HdrHudlessCapture {
        void capture(RtContext ctx, VkCommandBuffer command, MemoryStack stack, RtImage source);
    }

    public void destroyImages() {
        if (displayImage != null) {
            displayImage.destroy();
            displayImage = null;
        }
        if (hdrDisplayImage != null) {
            hdrDisplayImage.destroy();
            hdrDisplayImage = null;
        }
    }

    public void destroyPipelineAndExposure() {
        exposure.destroy();
        if (displayPipeline != null) {
            displayPipeline.destroy();
            displayPipeline = null;
        }
    }

    /** Device-idle teardown for presentation resources. */
    public void destroyPresentationAfterDeviceIdle() {
        if (hdrCompositePipeline != null) hdrCompositePipeline.destroy();
        if (sdrPresentPipeline != null) sdrPresentPipeline.destroy();
        if (sdrPresentImage != null) sdrPresentImage.destroy();
        RtContext ctx = RtContext.currentOrNull();
        if (ctx != null && hdrUiSampler != 0L) VK10.vkDestroySampler(ctx.vk(), hdrUiSampler, null);
        hdrCompositePipeline = null;
        sdrPresentPipeline = null;
        sdrPresentImage = null;
        hdrUiSampler = 0L;
        hdrWrittenThisFrame = false;
    }

    private static VkImageCopy.Buffer copyRegion(MemoryStack stack, int width, int height) {
        VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
        region.get(0).srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.get(0).dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.get(0).extent().set(width, height, 1);
        return region;
    }
}
