package dev.comfyfluffy.caustica.rt.upscale;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;

import java.util.Objects;

/** Explicit 1:1/off backend and fallback for unavailable temporal upscalers. */
public final class NativeUpscalerBackend implements UpscalerBackend<NativeUpscalerBackend.Request> {
    @Override
    public boolean available() {
        return true;
    }

    @Override
    public FrameContext.Extent recommendedRenderExtent(int displayWidth, int displayHeight) {
        return new FrameContext.Extent(displayWidth, displayHeight);
    }

    @Override
    public void requestReset() {
        // Stateless: native/off has no temporal history.
    }

    @Override
    public UpscaleResult execute(Request input) {
        Objects.requireNonNull(input, "input");
        dev.comfyfluffy.caustica.rt.graph.UpscalerBarriers.before(input.command(), input.stack(),
                dev.comfyfluffy.caustica.rt.graph.UpscalerBarrierPlan.Backend.NATIVE, "produce",
                dev.comfyfluffy.caustica.rewrite.RewriteGates.upscalerBarriersV2());
        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(
                input.context(), input.command(), "fallback upscale");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.upscale")) {
            VkImageBlit.Buffer region = VkImageBlit.calloc(1, input.stack());
            region.get(0).srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                    .baseArrayLayer(0).layerCount(1);
            region.get(0).dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                    .baseArrayLayer(0).layerCount(1);
            region.get(0).srcOffsets(1).set(input.source().width, input.source().height, 1);
            region.get(0).dstOffsets(1).set(input.output().width, input.output().height, 1);
            VK10.vkCmdBlitImage(input.command(), input.source().image, VK10.VK_IMAGE_LAYOUT_GENERAL,
                    input.output().image, VK10.VK_IMAGE_LAYOUT_GENERAL, region, VK10.VK_FILTER_LINEAR);
        }
        return new UpscaleResult(input.output(), true);
    }

    @Override
    public void destroy() {
        // Stateless: no resources to release.
    }

    public record Request(RtContext context, VkCommandBuffer command, MemoryStack stack,
                          RtImage source, RtImage output) {
        public Request {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(output, "output");
        }
    }
}
