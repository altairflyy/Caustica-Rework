package dev.comfyfluffy.caustica.rt.frame;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FramePipelineTest {
    @Test
    void executesTheLegacyPassInDeclaredOrderWithTheSameFrame() {
        FrameContext frame = frame(12L);
        List<String> order = new ArrayList<>();
        List<FrameContext> received = new ArrayList<>();

        FramePipeline pipeline = new FramePipeline(
                new LegacyCompositePass(context -> {
                    order.add("legacy");
                    received.add(context);
                }));

        pipeline.execute(frame);

        assertEquals(1, pipeline.passCount());
        assertEquals(List.of("legacy"), order);
        assertSame(frame, received.getFirst());
    }

    private static FrameContext frame(long index) {
        return new FrameContext(index, 1.0f / 60.0f,
                new FrameContext.Extent(1, 1), new FrameContext.Extent(1, 1),
                new FrameContext.Camera(0, 0, 0, new Matrix4f()),
                new FrameContext.Camera(0, 0, 0, new Matrix4f()),
                new FrameContext.Jitter(0, 0), new Object(), 0,
                FrameContext.LEGACY_SCENE_GENERATION);
    }
}
