package dev.comfyfluffy.caustica.rt.frame;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FramePipelineTest {
    @Test
    void executesPrepareThenLegacyWithTheSameFrame() {
        FrameContext frame = frame(12L);
        List<String> order = new ArrayList<>();
        List<FrameContext> received = new ArrayList<>();

        FramePipeline pipeline = new FramePipeline(
                new PrepareFramePass(context -> {
                    order.add("prepare");
                    received.add(context);
                }),
                new LegacyCompositePass(context -> {
                    order.add("legacy");
                    received.add(context);
                }));

        pipeline.execute(frame);

        assertEquals(2, pipeline.passCount());
        assertEquals(List.of("prepare", "legacy"), order);
        assertSame(frame, received.getFirst());
        assertSame(frame, received.getLast());
    }

    @Test
    void pathTracePassDelegatesExactlyOnceWithTheSameFrame() {
        FrameContext frame = frame(13L);
        List<FrameContext> received = new ArrayList<>();

        new PathTracePass(received::add).execute(frame);

        assertEquals(1, received.size());
        assertSame(frame, received.getFirst());
    }

    @Test
    void reconstructionPassDelegatesExactlyOnceWithTheSameFrame() {
        FrameContext frame = frame(14L);
        List<FrameContext> received = new ArrayList<>();

        new ReconstructionPass(received::add).execute(frame);

        assertEquals(1, received.size());
        assertSame(frame, received.getFirst());
    }

    @Test
    void upscalePassDelegatesExactlyOnceWithTheSameFrame() {
        FrameContext frame = frame(15L);
        List<FrameContext> received = new ArrayList<>();

        new UpscalePass(received::add).execute(frame);

        assertEquals(1, received.size());
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
