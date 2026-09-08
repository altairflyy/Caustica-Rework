package dev.comfyfluffy.caustica.rt.reconstruction.experimental.nrd;

import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.pipeline.RtNrdDenoiser;
import org.joml.Matrix4fc;

/**
 * Quarantine boundary for the retained NRD/REBLUR experiment.
 *
 * <p>The Java source and native shim remain available for investigation, but NRD is not a
 * production baseline and cannot be selected by configuration or native-library availability.
 */
public final class ExperimentalNrdBackend {
    private final RtNrdDenoiser delegate = RtNrdDenoiser.INSTANCE;

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

    public void destroy() {
        delegate.destroy();
    }
}
