package dev.comfyfluffy.caustica.rt.frame;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameContextTest {
    @Test
    void capturesRequiredFrameDataAsAnImmutableRecord() {
        Matrix4f matrix = new Matrix4f().translation(1.0f, 2.0f, 3.0f);
        FrameContext context = new FrameContext(7L, 1.0f / 60.0f,
                new FrameContext.Extent(1920, 1080), new FrameContext.Extent(1280, 720),
                new FrameContext.Camera(1, 2, 3, matrix),
                new FrameContext.Camera(0, 1, 2, matrix),
                new FrameContext.Jitter(0.25f, -0.125f), new Object(), 2,
                FrameContext.LEGACY_SCENE_GENERATION);

        assertEquals(7L, context.frameIndex());
        assertEquals(1280, context.renderExtent().width());
        assertEquals(FrameContext.LEGACY_SCENE_GENERATION, context.sceneGeneration());
        assertTrue(Modifier.isFinal(FrameContext.class.getModifiers()));
        assertNotSame(context.currentCamera().viewProjection(), context.currentCamera().viewProjection());
    }

    @Test
    void rejectsInvalidScalarSnapshots() {
        assertThrows(IllegalArgumentException.class, () -> new FrameContext.Extent(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new FrameContext.Jitter(Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameContext(0, -1,
                new FrameContext.Extent(1, 1), new FrameContext.Extent(1, 1),
                new FrameContext.Camera(0, 0, 0, new Matrix4f()),
                new FrameContext.Camera(0, 0, 0, new Matrix4f()),
                new FrameContext.Jitter(0, 0), null, 0, 0));
    }
}
