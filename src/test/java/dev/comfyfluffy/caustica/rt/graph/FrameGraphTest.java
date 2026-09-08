package dev.comfyfluffy.caustica.rt.graph;

import dev.comfyfluffy.caustica.rt.frame.FramePipeline;
import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import dev.comfyfluffy.caustica.rt.frame.PathTracePass;
import dev.comfyfluffy.caustica.rt.frame.PostPresentPass;
import dev.comfyfluffy.caustica.rt.frame.PrepareFramePass;
import dev.comfyfluffy.caustica.rt.frame.ReconstructionPass;
import dev.comfyfluffy.caustica.rt.frame.UpscalePass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameGraphTest {
    @Test
    void shadowTopologicalOrderMatchesExistingFramePipelineOrder() {
        Consumer<FrameContext> noOp = frame -> { };
        FramePipeline pipeline = new FramePipeline(
                new PrepareFramePass(noOp),
                new PathTracePass(noOp),
                new ReconstructionPass(noOp),
                new UpscalePass(noOp),
                new PostPresentPass(noOp));

        FrameGraph graph = FrameGraph.shadow(pipeline);

        assertEquals(pipeline.declaredPassNames(), graph.topologicalOrderNames());
        assertEquals(List.of("PrepareFramePass", "PathTracePass", "ReconstructionPass",
                "UpscalePass", "PostPresentPass"), graph.topologicalOrderNames());
        assertEquals(5, graph.passes().size());
        assertEquals(4, graph.accesses().size());
        assertTrue(graph.diagnostics().isEmpty(), graph.diagnostics().toString());
        assertTrue(graph.passes().stream().allMatch(pass -> !pass.resources().isEmpty()));
        assertTrue(graph.passes().stream().flatMap(pass -> pass.resources().stream())
                .allMatch(use -> !use.stages().isEmpty()));
    }
}
