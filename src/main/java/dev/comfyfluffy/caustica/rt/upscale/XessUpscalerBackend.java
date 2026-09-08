package dev.comfyfluffy.caustica.rt.upscale;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import dev.comfyfluffy.caustica.rt.pipeline.RtXessUpscaler;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Objects;

/** Production adapter for the capability-gated Intel XeSS upscaler. */
public final class XessUpscalerBackend implements UpscalerBackend<XessUpscalerBackend.Request> {
    private final RtXessUpscaler delegate;
    private double previousCameraX;
    private double previousCameraY;
    private double previousCameraZ;
    private boolean cameraValid;

    public XessUpscalerBackend() {
        this(RtXessUpscaler.INSTANCE);
    }

    XessUpscalerBackend(RtXessUpscaler delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean available() {
        return RtXessUpscaler.enabled();
    }

    public int quality() {
        return RtXessUpscaler.quality();
    }

    @Override
    public FrameContext.Extent recommendedRenderExtent(int displayWidth, int displayHeight) {
        int[] extent = delegate.queryRenderSize(displayWidth, displayHeight);
        return extent == null
                ? new FrameContext.Extent(displayWidth, displayHeight)
                : new FrameContext.Extent(extent[0], extent[1]);
    }

    public boolean releaseIfDisabled() {
        return delegate.releaseIfDisabled();
    }

    @Override
    public void requestReset() {
        delegate.requestReset();
    }

    @Override
    public UpscaleResult execute(Request input) {
        Objects.requireNonNull(input, "input");
        if (!delegate.ensureFeature(input.displayWidth(), input.displayHeight())) {
            return new UpscaleResult(input.source(), false);
        }
        boolean completed;
        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(
                input.context(), input.command(), "XeSS upscale");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.xess")) {
            if (cameraValid) {
                double dx = input.cameraX() - previousCameraX;
                double dy = input.cameraY() - previousCameraY;
                double dz = input.cameraZ() - previousCameraZ;
                if (dx * dx + dy * dy + dz * dz > 32.0 * 32.0) {
                    input.onTeleportReset().run();
                }
            }
            previousCameraX = input.cameraX();
            previousCameraY = input.cameraY();
            previousCameraZ = input.cameraZ();
            cameraValid = true;
            float jitterX = input.jitterAlreadyIntegrated() ? 0.0f : input.jitterX();
            float jitterY = input.jitterAlreadyIntegrated() ? 0.0f : input.jitterY();
            completed = delegate.evaluate(input.command().address(), input.source(), input.depth(), input.motion(),
                    input.output(), input.renderWidth(), input.renderHeight(),
                    input.displayWidth(), input.displayHeight(), jitterX, jitterY);
        }
        return new UpscaleResult(completed ? input.output() : input.source(), completed);
    }

    @Override
    public void destroy() {
        delegate.destroy();
        cameraValid = false;
    }

    public record Request(RtContext context, VkCommandBuffer command,
                          RtImage source, RtImage depth, RtImage motion, RtImage output,
                          int renderWidth, int renderHeight, int displayWidth, int displayHeight,
                          float jitterX, float jitterY, boolean jitterAlreadyIntegrated,
                          double cameraX, double cameraY, double cameraZ, Runnable onTeleportReset) {
        public Request {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(depth, "depth");
            Objects.requireNonNull(motion, "motion");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(onTeleportReset, "onTeleportReset");
        }
    }
}
