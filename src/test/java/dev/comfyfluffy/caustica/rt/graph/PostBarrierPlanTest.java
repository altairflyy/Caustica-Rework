package dev.comfyfluffy.caustica.rt.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Mode.*;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Stage.*;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Layout.*;
import static org.junit.jupiter.api.Assertions.*;

class PostBarrierPlanTest {
    @Test void generatedBarriersRequireBothFlagsAndDefaultOff() {
        String graphKey = dev.comfyfluffy.caustica.rewrite.RewriteGates.RENDER_GRAPH_V2_KEY;
        String postKey = dev.comfyfluffy.caustica.rewrite.RewriteGates.POST_BARRIERS_V2_KEY;
        String oldGraph = System.getProperty(graphKey), oldPost = System.getProperty(postKey);
        try {
            System.clearProperty(graphKey);
            System.clearProperty(postKey);
            assertFalse(dev.comfyfluffy.caustica.rewrite.RewriteGates.postBarriersV2());
            for (boolean graph : List.of(false, true)) for (boolean post : List.of(false, true)) {
                System.setProperty(graphKey, Boolean.toString(graph));
                System.setProperty(postKey, Boolean.toString(post));
                assertEquals(graph && post, dev.comfyfluffy.caustica.rewrite.RewriteGates.postBarriersV2());
            }
        } finally {
            if (oldGraph == null) System.clearProperty(graphKey); else System.setProperty(graphKey, oldGraph);
            if (oldPost == null) System.clearProperty(postKey); else System.setProperty(postKey, oldPost);
        }
    }
    @Test void allFourModesMatchLegacyBarrierPositionsAndCounts() {
        for (boolean auto : List.of(false, true)) for (boolean hdr : List.of(false, true)) {
            PostBarrierPlan plan = PostBarrierPlan.of(auto, hdr);
            var required = plan.operations().stream().filter(op -> plan.before(op.name()).required())
                    .map(BarrierSynthesis.Operation::name).toList();
            assertEquals(auto ? List.of("histogram", "resolve", "display", "copy", "export")
                    : List.of("display", "copy", "export"), required);
            assertEquals(auto, plan.automaticExposure());
            assertSame(plan, PostBarrierPlan.of(auto, hdr));
            assertEquals(hdr, plan.operations().stream().flatMap(op -> op.uses().stream())
                    .anyMatch(use -> use.resource().equals("hdrDisplayImage")));
        }
    }

    @Test void manualExposureIsTransferWriteAndAutoIsComputeWrite() {
        assertEquals(TRANSFER, PostBarrierPlan.of(false, false).before("display").hazards().getFirst().before().stage());
        assertEquals(COMPUTE, PostBarrierPlan.of(true, false).before("display").hazards().getFirst().before().stage());
        var copy = PostBarrierPlan.of(true, false).before("copy").hazards().getFirst();
        assertEquals("displayImage", copy.resource());
        assertEquals(COMPUTE, copy.before().stage());
        assertEquals(TRANSFER, copy.after().stage());
        assertEquals(WRITE, copy.before().mode());
        assertEquals(READ, copy.after().mode());
    }

    @Test void compilerDetectsRawWarWawButNotReadRead() {
        for (var before : BarrierSynthesis.Mode.values()) for (var after : BarrierSynthesis.Mode.values()) {
            var plan = BarrierSynthesis.compile(List.of(op("a", before), op("b", after)), Set.of("image"));
            assertEquals(before != READ || after != READ, plan.get(1).required());
        }
    }

    @Test void compilerRetainsNonAdjacentDependencies() {
        var other = new BarrierSynthesis.Operation("other", List.of(
                new BarrierSynthesis.Use("otherImage", WRITE, COMPUTE, GENERAL)));
        var plan = BarrierSynthesis.compile(List.of(op("a", WRITE), other, op("b", READ)), Set.of());
        assertEquals("image", plan.get(2).hazards().getFirst().resource());
    }

    @Test void invalidDeclarationsFailBeforeRecording() {
        assertThrows(IllegalArgumentException.class, () -> BarrierSynthesis.compile(List.of(op("a", READ)), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> BarrierSynthesis.compile(List.of(op("a", WRITE), op("a", READ)), Set.of()));
        var wrongLayout = new BarrierSynthesis.Operation("b", List.of(new BarrierSynthesis.Use("image", READ, COMPUTE, BUFFER)));
        assertThrows(IllegalArgumentException.class, () -> BarrierSynthesis.compile(List.of(op("a", WRITE), wrongLayout), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> PostBarrierPlan.of(false, false).before("histogram"));
    }

    @Test void generatedScopeMatchesVerifiedMinecraftBarrierBytecode() {
        assertEquals(65536L, PostImageBarriers.STAGES);
        assertEquals(98304L, PostImageBarriers.ACCESS);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var dependency = PostImageBarriers.dependency(stack);
            assertEquals(0, dependency.dependencyFlags());
            assertEquals(1, dependency.memoryBarrierCount());
            assertEquals(0, dependency.imageMemoryBarrierCount());
            assertEquals(0, dependency.bufferMemoryBarrierCount());
            var barrier = dependency.pMemoryBarriers().get(0);
            assertEquals(65536L, barrier.srcStageMask());
            assertEquals(65536L, barrier.dstStageMask());
            assertEquals(98304L, barrier.srcAccessMask());
            assertEquals(98304L, barrier.dstAccessMask());
        }
    }

    private BarrierSynthesis.Operation op(String name, BarrierSynthesis.Mode mode) {
        return new BarrierSynthesis.Operation(name, List.of(new BarrierSynthesis.Use("image", mode, COMPUTE, GENERAL)));
    }
}
