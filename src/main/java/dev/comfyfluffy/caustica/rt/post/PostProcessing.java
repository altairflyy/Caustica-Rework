package dev.comfyfluffy.caustica.rt.post;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.pipeline.RtDisplayPipeline;
import dev.comfyfluffy.caustica.rt.pipeline.RtExposure;
import dev.comfyfluffy.caustica.rt.graph.PostBarrierPlan;
import dev.comfyfluffy.caustica.rt.graph.PostImageBarriers;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCopy;

/**
 * Owner of exposure, display mapping and its SDR/HDR targets.
 * Caller keeps the legacy synchronization and lifecycle boundaries: image release,
 * image allocation and pipeline destruction must not be coalesced across other owners.
 */
public final class PostProcessing {
    private RtDisplayPipeline displayPipeline;
    private final RtExposure exposure = new RtExposure();
    private RtImage displayImage;
    private RtImage hdrDisplayImage;
    private int loggedPostBarrierMode = -1;

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
                       int displayW, int displayH, long dstImage, boolean postHdr,
                       Runnable displayWritten) {
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
        displayWritten.run();
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

    private static VkImageCopy.Buffer copyRegion(MemoryStack stack, int width, int height) {
        VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
        region.get(0).srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.get(0).dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.get(0).extent().set(width, height, 1);
        return region;
    }
}
