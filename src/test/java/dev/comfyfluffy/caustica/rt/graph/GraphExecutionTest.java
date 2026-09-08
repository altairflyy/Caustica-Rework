package dev.comfyfluffy.caustica.rt.graph;

import dev.comfyfluffy.caustica.rewrite.RewriteGates;
import dev.comfyfluffy.caustica.rt.frame.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class GraphExecutionTest {
    @Test void bothPathsPreserveCallbacksInterleavingAndFrameIdentity() {
        assertEquals(run(false, -1), run(true, -1));
    }

    @Test void failureAtEveryBoundaryPreservesLegacyAdvancementAndPropagation() {
        for (int failure = 0; failure < 5; failure++) {
            assertEquals(run(false, failure), run(true, failure));
        }
    }

    private List<String> run(boolean graph, int failure) {
        String old = System.getProperty(RewriteGates.RENDER_GRAPH_V2_KEY);
        try {
            System.setProperty(RewriteGates.RENDER_GRAPH_V2_KEY, Boolean.toString(graph));
            List<String> events = new ArrayList<>();
            FrameContext frame = new FrameContext(1, 1f / 60,
                    new FrameContext.Extent(1, 1), new FrameContext.Extent(1, 1),
                    new FrameContext.Camera(0, 0, 0, new Matrix4f()),
                    new FrameContext.Camera(0, 0, 0, new Matrix4f()),
                    new FrameContext.Jitter(0, 0), new Object(), 0, FrameContext.LEGACY_SCENE_GENERATION);
            RuntimeException expected = new RuntimeException("callback failure");
            List<Consumer<FrameContext>> callbacks = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                int index = i;
                callbacks.add(received -> {
                    assertSame(frame, received);
                    events.add("pass" + index);
                    if (index == failure) throw expected;
                });
            }
            FramePipeline pipeline = new FramePipeline(new PrepareFramePass(callbacks.get(0)),
                    new PathTracePass(callbacks.get(1)), new ReconstructionPass(callbacks.get(2)),
                    new UpscalePass(callbacks.get(3)), new PostPresentPass(callbacks.get(4)));
            GraphExecution execution = new GraphExecution(FrameGraph.shadow(pipeline), pipeline);
            FrameCursor cursor = execution.begin(frame);
            assertEquals(!graph, cursor instanceof FramePipeline.Cursor);
            // Selection is fixed even if the property is changed after begin.
            System.setProperty(RewriteGates.RENDER_GRAPH_V2_KEY, Boolean.toString(!graph));
            for (int i = 0; i < 5; i++) {
                assertFalse(cursor.complete());
                events.add("before" + i);
                if (i == failure) {
                    assertSame(expected, assertThrows(RuntimeException.class, cursor::executeNext));
                } else {
                    cursor.executeNext();
                }
                events.add("after" + i);
            }
            assertTrue(cursor.complete());
            assertThrows(IllegalStateException.class, cursor::executeNext);
            assertThrows(NullPointerException.class, () -> execution.begin(null));
            return events;
        } finally {
            if (old == null) System.clearProperty(RewriteGates.RENDER_GRAPH_V2_KEY);
            else System.setProperty(RewriteGates.RENDER_GRAPH_V2_KEY, old);
        }
    }
}
