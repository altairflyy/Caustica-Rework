package dev.comfyfluffy.caustica.rt.reconstruction;

import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.RtFramePresenter;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.pipeline.RtSvgfDenoiser;
import dev.comfyfluffy.caustica.rt.graph.DenoiserBarrierPlan;
import dev.comfyfluffy.caustica.rt.graph.DenoiserBarriers;
import dev.comfyfluffy.caustica.rewrite.RewriteGates;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCopy;

import java.util.Objects;

/** Runtime SVGF implementation and sole owner of its pipeline and temporal resources. */
public final class SvgfReconstructionBackend
        implements ReconstructionBackend<SvgfReconstructionBackend.Request> {
    private static final float MAX_FRAMES = 48.0f;
    private static final int DEBUG_FIRST = 10;
    private static final int DEBUG_LAST = 12;
    private static final float PHI_LUMINANCE = 4.0f;
    private static final float PHI_NORMAL = 128.0f;
    private static final float PHI_DEPTH = 2.0f;

    private final SvgfResources resources = new SvgfResources();
    private RtSvgfDenoiser denoiser;
    private int loggedBarrierMode = -1;

    public static boolean isDebugView(int debugView) {
        return debugView >= DEBUG_FIRST && debugView <= DEBUG_LAST;
    }

    /** Recreates the reference working set after the caller has synchronized a resize. */
    public void ensureResources(RtContext context, int width, int height) {
        resources.allocate(context, width, height);
        if (denoiser == null) {
            denoiser = RtSvgfDenoiser.create(context);
            CausticaMod.LOGGER.info("SVGF denoiser active ({} a-trous passes, {} frame window)",
                    RtSvgfDenoiser.ATROUS_PASSES, (int) MAX_FRAMES);
        } else {
            denoiser.invalidateBindings();
        }
    }

    /** Releases extent-dependent images while retaining the reusable pipelines. */
    public void releaseResources() {
        resources.destroy();
    }

    @Override
    public boolean available() {
        return denoiser != null && resources.historyPing() != null;
    }

    @Override
    public void requestReset() {
        resources.resetHistory();
    }

    @Override
    public ReconstructionResult execute(Request input) {
        Objects.requireNonNull(input, "input");
        if (!available()) {
            return new ReconstructionResult(input.source(), false);
        }

        boolean writeToPing = resources.writeToPing();
        int parity = writeToPing ? 0 : 1;
        RtImage historyIn = writeToPing ? resources.historyPong() : resources.historyPing();
        RtImage historyOut = writeToPing ? resources.historyPing() : resources.historyPong();
        RtImage momentsIn = writeToPing ? resources.momentsPong() : resources.momentsPing();
        RtImage momentsOut = writeToPing ? resources.momentsPing() : resources.momentsPong();
        boolean reset = !resources.hasHistory();
        float cameraForwardDelta = cameraForwardDelta(input, reset);
        int extraSkySmooth = RtFramePresenter.INSTANCE.isActive() ? 1 : 0;

        boolean generatedBarriers = RewriteGates.denoiserBarriersV2();
        DenoiserBarrierPlan barrierPlan = DenoiserBarrierPlan.svgf(
                writeToPing, RtSvgfDenoiser.ATROUS_PASSES, RtSvgfDenoiser.HISTORY_FEEDBACK_PASS);
        RtImage reconstructed;
        DenoiserBarriers.before(input.command(), input.stack(), barrierPlan,
                DenoiserBarrierPlan.REPROJECT, generatedBarriers);
        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(
                input.context(), input.command(), "SVGF denoise");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.svgf")) {
            denoiser.reproject(input.command(), input.width(), input.height(), parity,
                    input.source().view, historyIn.view, momentsIn.view,
                    historyOut.view, momentsOut.view, resources.filterPing().view,
                    input.motion().view, input.viewZ().view, input.normal().view,
                    resources.previousViewZ().view, resources.previousNormal().view, input.albedo().view,
                    reset, MAX_FRAMES, cameraForwardDelta);
            RtImage source = resources.filterPing();
            RtImage destination = resources.filterPong();
            for (int pass = 0; pass < RtSvgfDenoiser.ATROUS_PASSES; pass++) {
                DenoiserBarriers.before(input.command(), input.stack(), barrierPlan,
                        DenoiserBarrierPlan.atrous(pass), generatedBarriers);
                boolean lastPass = pass == RtSvgfDenoiser.ATROUS_PASSES - 1;
                denoiser.atrous(input.command(), input.width(), input.height(), pass, parity,
                        source.view, destination.view, input.viewZ().view, input.normal().view,
                        momentsOut.view, input.albedo().view,
                        PHI_LUMINANCE, PHI_NORMAL, PHI_DEPTH,
                        extraSkySmooth, lastPass,
                        isDebugView(input.debugView()) ? input.debugView() : 0);
                if (pass == RtSvgfDenoiser.HISTORY_FEEDBACK_PASS) {
                    DenoiserBarriers.before(input.command(), input.stack(), barrierPlan,
                            DenoiserBarrierPlan.HISTORY_FEEDBACK, generatedBarriers);
                    copyImage(input.command(), input.stack(), destination, historyOut);
                }
                RtImage swap = source;
                source = destination;
                destination = swap;
            }
            DenoiserBarriers.before(input.command(), input.stack(), barrierPlan,
                    DenoiserBarrierPlan.PREVIOUS_GUIDES, generatedBarriers);
            copyImage(input.command(), input.stack(), input.viewZ(), resources.previousViewZ());
            copyImage(input.command(), input.stack(), input.normal(), resources.previousNormal());
            DenoiserBarriers.before(input.command(), input.stack(), barrierPlan,
                    DenoiserBarrierPlan.EXPORT, generatedBarriers);

            reconstructed = source;
        }
        resources.flipHistory();
        resources.markHistoryValid();
        resources.snapshotPreviousCamera(input.cameraX(), input.cameraY(), input.cameraZ());
        int barrierMode = generatedBarriers ? 1 : 0;
        if (loggedBarrierMode != barrierMode) {
            CausticaMod.LOGGER.info("AER-083 denoiser barriers: path={}, backend=SVGF, scope=legacy-conservative",
                    generatedBarriers ? "generated" : "legacy");
            loggedBarrierMode = barrierMode;
        }
        return new ReconstructionResult(reconstructed, true);
    }

    private float cameraForwardDelta(Request input, boolean reset) {
        if (!resources.hasHistory() || reset) {
            return 0.0f;
        }
        double fx = input.viewRotation().m02();
        double fy = input.viewRotation().m12();
        double fz = input.viewRotation().m22();
        float delta = (float) -((input.cameraX() - resources.previousCameraX()) * fx
                + (input.cameraY() - resources.previousCameraY()) * fy
                + (input.cameraZ() - resources.previousCameraZ()) * fz);
        return Float.isFinite(delta) ? delta : 0.0f;
    }

    @Override
    public void destroy() {
        if (denoiser != null) {
            denoiser.destroy();
            denoiser = null;
        }
        resources.destroy();
    }

    private static void copyImage(VkCommandBuffer command, MemoryStack stack, RtImage source, RtImage destination) {
        VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
        region.get(0).srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                .baseArrayLayer(0).layerCount(1);
        region.get(0).dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                .baseArrayLayer(0).layerCount(1);
        region.get(0).extent().set(
                Math.min(source.width, destination.width), Math.min(source.height, destination.height), 1);
        VK10.vkCmdCopyImage(command, source.image, VK10.VK_IMAGE_LAYOUT_GENERAL,
                destination.image, VK10.VK_IMAGE_LAYOUT_GENERAL, region);
    }

    public record Request(RtContext context, VkCommandBuffer command, MemoryStack stack,
                          RtImage source, RtImage motion, RtImage viewZ, RtImage normal, RtImage albedo,
                          int width, int height, int debugView, Matrix4fc viewRotation,
                          double cameraX, double cameraY, double cameraZ) {
        public Request {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(motion, "motion");
            Objects.requireNonNull(viewZ, "viewZ");
            Objects.requireNonNull(normal, "normal");
            Objects.requireNonNull(albedo, "albedo");
            Objects.requireNonNull(viewRotation, "viewRotation");
        }
    }
}
