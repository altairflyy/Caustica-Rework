package dev.comfyfluffy.caustica.rt.reconstruction;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.pipeline.RtDlssRr;
import org.joml.Matrix4fc;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Objects;

/** Production adapter that exposes the existing DLSS Ray Reconstruction implementation. */
public final class DlssRrReconstructionBackend
        implements ReconstructionBackend<DlssRrReconstructionBackend.Request> {
    private final RtDlssRr delegate;

    public DlssRrReconstructionBackend() {
        this(RtDlssRr.INSTANCE);
    }

    DlssRrReconstructionBackend(RtDlssRr delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean available() {
        return RtDlssRr.enabled();
    }

    public int quality() {
        return RtDlssRr.quality();
    }

    public int[] recommendedRenderExtent(int displayWidth, int displayHeight) {
        return delegate.queryOptimalRenderSize(displayWidth, displayHeight);
    }

    public boolean releaseIfDisabled() {
        return delegate.releaseIfDisabled();
    }

    @Override
    public void requestReset() {
        delegate.requestReset();
    }

    @Override
    public ReconstructionResult execute(Request input) {
        Objects.requireNonNull(input, "input");
        if (!delegate.ensureFeature(input.command().address(), input.renderWidth(), input.renderHeight(),
                input.displayWidth(), input.displayHeight())) {
            return new ReconstructionResult(input.color(), false);
        }
        boolean completed;
        try (RtDebugLabels.Scope ignored = RtDebugLabels.scope(
                input.context(), input.command(), "DLSS-RR evaluate");
             RtFrameStats.Scope ignoredStats = RtFrameStats.FRAME.stage("frame.dlssRr")) {
            completed = delegate.evaluate(input.command().address(), input.color(), input.depth(), input.motion(),
                    input.diffuseAlbedo(), input.specularAlbedo(), input.normals(), input.specularMotion(),
                    input.output(), input.renderWidth(), input.renderHeight(), input.displayWidth(),
                    input.displayHeight(), input.jitterX(), input.jitterY(),
                    input.worldToView(), input.viewToClip());
        }
        return new ReconstructionResult(completed ? input.output() : input.color(), completed);
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    public record Request(RtContext context, VkCommandBuffer command,
                          RtImage color, RtImage depth, RtImage motion,
                          RtImage diffuseAlbedo, RtImage specularAlbedo, RtImage normals,
                          RtImage specularMotion, RtImage output,
                          int renderWidth, int renderHeight, int displayWidth, int displayHeight,
                          float jitterX, float jitterY, Matrix4fc worldToView, Matrix4fc viewToClip) {
        public Request {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(depth, "depth");
            Objects.requireNonNull(motion, "motion");
            Objects.requireNonNull(diffuseAlbedo, "diffuseAlbedo");
            Objects.requireNonNull(specularAlbedo, "specularAlbedo");
            Objects.requireNonNull(normals, "normals");
            Objects.requireNonNull(specularMotion, "specularMotion");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(worldToView, "worldToView");
            Objects.requireNonNull(viewToClip, "viewToClip");
        }
    }
}
