package dev.comfyfluffy.caustica.rt.reconstruction.experimental.nrd;

import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.pipeline.RtNrdDenoiser;
import dev.comfyfluffy.caustica.rt.pipeline.RtNrdCombinePipeline;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.joml.Matrix4fc;

/**
 * Quarantine boundary for the retained NRD/REBLUR experiment.
 *
 * <p>The Java source and native shim remain available for investigation, but NRD is not a
 * production baseline and cannot be selected by configuration or native-library availability.
 */
public final class ExperimentalNrdBackend {
    private static final float DENOISING_RANGE = 500000.0f;

    private final RtNrdDenoiser delegate = RtNrdDenoiser.INSTANCE;
    private RtNrdCombinePipeline combinePipeline;

    /** Always false while the runtime path remains retired. */
    public boolean selected() {
        return false;
    }

    public void resetHistory() {
        delegate.resetHistory();
    }

    public boolean denoise(long command, int renderWidth, int renderHeight,
                           RtImage motion, RtImage normalRoughness, RtImage viewZ,
                           RtImage diffuseInput, RtImage specularInput,
                           RtImage diffuseOutput, RtImage specularOutput, RtImage validation,
                           Matrix4fc viewToClip, Matrix4fc viewRotation,
                           double cameraWorldX, double cameraWorldY, double cameraWorldZ,
                           double anchorX, double anchorY, double anchorZ,
                           float jitterPixelsX, float jitterPixelsY, int frameIndex,
                           boolean projectionChanged) {
        return delegate.denoise(command, renderWidth, renderHeight,
                motion, normalRoughness, viewZ, diffuseInput, specularInput,
                diffuseOutput, specularOutput, validation, viewToClip, viewRotation,
                cameraWorldX, cameraWorldY, cameraWorldZ, anchorX, anchorY, anchorZ,
                jitterPixelsX, jitterPixelsY, frameIndex, projectionChanged);
    }

    /** Create or rebind the backend-owned combine pipeline to borrowed frame-sized views. */
    public void bindCombine(RtContext ctx, NrdFrameViews views) {
        if (combinePipeline == null) {
            combinePipeline = RtNrdCombinePipeline.create(ctx);
        } else {
            combinePipeline.invalidateBindings();
        }
        combinePipeline.setImages(views.diffuseOutputView(), views.specularOutputView(),
                views.combinedOutputView(), views.rawColorView(), views.albedoView(),
                views.viewZView(), views.specularAlbedoView(), views.normalView());
    }

    /** Re-modulate and combine the denoised lobes using the unchanged dispatch geometry. */
    public void combine(VkCommandBuffer command, int renderWidth, int renderHeight) {
        combinePipeline.dispatch(command, renderWidth, renderHeight, DENOISING_RANGE);
    }

    public void destroy() {
        delegate.destroy();
        if (combinePipeline != null) {
            combinePipeline.destroy();
            combinePipeline = null;
        }
    }

    /** Immutable borrowed image-view subset required by the combine pass. */
    public record NrdFrameViews(long diffuseOutputView, long specularOutputView,
                                long combinedOutputView, long rawColorView,
                                long albedoView, long viewZView,
                                long specularAlbedoView, long normalView) {
    }
}
