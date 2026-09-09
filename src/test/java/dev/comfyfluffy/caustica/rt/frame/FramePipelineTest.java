package dev.comfyfluffy.caustica.rt.frame;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import dev.comfyfluffy.caustica.rt.graph.FrameGraph;
import dev.comfyfluffy.caustica.rt.graph.GraphExecution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FramePipelineTest {
    @Test
    void executesAllMacroPassesInDeclaredOrderWithTheSameFrame() {
        FrameContext frame = frame(12L);
        List<String> order = new ArrayList<>();
        List<FrameContext> received = new ArrayList<>();

        FramePipeline pipeline = new FramePipeline(
                new PrepareFramePass(context -> pass("prepare", order, received).execute(context)),
                new PathTracePass(context -> pass("pathTrace", order, received).execute(context)),
                new ReconstructionPass(context -> pass("reconstruction", order, received).execute(context)),
                new UpscalePass(context -> pass("upscale", order, received).execute(context)),
                new PostPresentPass(context -> pass("postPresent", order, received).execute(context)));

        FrameCursor cursor = new GraphExecution(FrameGraph.shadow(pipeline), pipeline).begin(frame);
        while (!cursor.complete()) cursor.executeNext();

        assertEquals(5, pipeline.passCount());
        assertEquals(List.of("prepare", "pathTrace", "reconstruction", "upscale", "postPresent"), order);
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

    @Test
    void postPresentPassDelegatesExactlyOnceWithTheSameFrame() {
        FrameContext frame = frame(17L);
        List<FrameContext> received = new ArrayList<>();

        new PostPresentPass(received::add).execute(frame);

        assertEquals(1, received.size());
        assertSame(frame, received.getFirst());
    }

    @Test
    void cursorPreservesOrderAcrossIncrementalExecution() {
        FrameContext frame = frame(16L);
        List<String> order = new ArrayList<>();
        FramePipeline pipeline = new FramePipeline(
                new PrepareFramePass(context -> order.add("first")),
                new PathTracePass(context -> order.add("second")),
                new ReconstructionPass(context -> order.add("third")),
                new UpscalePass(context -> order.add("fourth")),
                new PostPresentPass(context -> order.add("fifth")));
        FrameCursor cursor = new GraphExecution(FrameGraph.shadow(pipeline), pipeline).begin(frame);

        cursor.executeNext();
        assertEquals(List.of("first"), order);
        cursor.executeNext();

        assertEquals(List.of("first", "second"), order);
        assertEquals(false, cursor.complete());
        cursor.executeNext();
        cursor.executeNext();
        cursor.executeNext();
        assertEquals(List.of("first", "second", "third", "fourth", "fifth"), order);
        assertEquals(true, cursor.complete());
    }

    @Test
    void productionPipelineDeclaresEveryGateThreePassInOrder() throws IOException {
        Path sourcePath = Path.of("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
        String source = Files.readString(sourcePath);

        int pipeline = source.indexOf("private final FramePipeline framePipeline = new FramePipeline(");
        int prepare = source.indexOf("prepareFramePass", pipeline);
        int pathTrace = source.indexOf("pathTracePass", prepare);
        int reconstruction = source.indexOf("reconstructionPass", pathTrace);
        int upscale = source.indexOf("upscalePass", reconstruction);
        int postPresent = source.indexOf("postPresentPass", upscale);

        assertEquals(true, pipeline >= 0);
        assertEquals(true, prepare < pathTrace && pathTrace < reconstruction
                && reconstruction < upscale && upscale < postPresent);
    }

    private static FramePass pass(String name, List<String> order, List<FrameContext> received) {
        return context -> {
            order.add(name);
            received.add(context);
        };
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
