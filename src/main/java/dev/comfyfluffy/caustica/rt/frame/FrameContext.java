package dev.comfyfluffy.caustica.rt.frame;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Objects;

/**
 * Immutable snapshot of data already available at the render-frame seam.
 *
 * <p>AER-011 deliberately does not own GPU resources or temporal state. The
 * legacy RtComposite path remains the owner and consumer. A scene generation
 * of zero means that the legacy renderer has no canonical scene-generation
 * token yet; it must not be replaced with light or material generation.</p>
 */
public record FrameContext(
        long frameIndex,
        float deltaTimeSeconds,
        Extent displayExtent,
        Extent renderExtent,
        Camera currentCamera,
        Camera previousCamera,
        Jitter jitter,
        Object worldIdentity,
        int dimension,
        long sceneGeneration
) {
    public static final long LEGACY_SCENE_GENERATION = 0L;

    public FrameContext {
        if (!Float.isFinite(deltaTimeSeconds) || deltaTimeSeconds < 0.0f) {
            throw new IllegalArgumentException("deltaTimeSeconds must be finite and non-negative");
        }
        displayExtent = Objects.requireNonNull(displayExtent, "displayExtent");
        renderExtent = Objects.requireNonNull(renderExtent, "renderExtent");
        currentCamera = Objects.requireNonNull(currentCamera, "currentCamera");
        previousCamera = Objects.requireNonNull(previousCamera, "previousCamera");
        jitter = Objects.requireNonNull(jitter, "jitter");
    }

    public record Extent(int width, int height) {
        public Extent {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("extent must be positive");
            }
        }
    }

    public record Jitter(float x, float y) {
        public Jitter {
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                throw new IllegalArgumentException("jitter must be finite");
            }
        }
    }

    /** Camera matrices are defensively copied because JOML matrices are mutable. */
    public record Camera(double x, double y, double z, Matrix4fc viewProjection) {
        public Camera {
            viewProjection = new Matrix4f(Objects.requireNonNull(viewProjection, "viewProjection"));
        }

        @Override
        public Matrix4fc viewProjection() {
            return new Matrix4f(viewProjection);
        }
    }
}
