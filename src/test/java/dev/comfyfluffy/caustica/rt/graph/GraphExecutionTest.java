package dev.comfyfluffy.caustica.rt.graph;

import dev.comfyfluffy.caustica.rt.frame.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GraphExecutionTest {
    @Test void graphPathPreservesCallbacksInterleavingAndFrameIdentity() {
        assertEquals(List.of("before0", "pass0", "after0", "before1", "pass1", "after1",
                "before2", "pass2", "after2", "before3", "pass3", "after3",
                "before4", "pass4", "after4"), run(-1));
    }

    @Test void failureAtEveryBoundaryPreservesLegacyAdvancementAndPropagation() {
        for (int failure = 0; failure < 5; failure++) {
            run(failure);
        }
    }

    @Test void productionExecutionHasNoLegacyCursorBranch() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/graph/GraphExecution.java"));
        assertFalse(source.contains("RewriteGates"));
        assertFalse(source.contains("legacy.begin("));
        assertTrue(source.contains("return new Cursor(frame);"));
        assertEquals(List.of(), java.util.Arrays.stream(FramePipeline.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.equals("begin") || name.equals("execute")).toList());
        assertEquals(0, FramePipeline.class.getDeclaredClasses().length);
    }

    private List<String> run(int failure) {
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
    }
}
