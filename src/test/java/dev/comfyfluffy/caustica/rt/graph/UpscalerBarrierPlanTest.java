package dev.comfyfluffy.caustica.rt.graph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpscalerBarrierPlanTest {
    @Test void everySuccessfulProducerExportsOnlyItsDisplayOutput() {
        for (var backend : UpscalerBarrierPlan.Backend.values()) {
            var barrier = UpscalerBarrierPlan.before(backend, "export");
            assertEquals(1, barrier.hazards().size());
            var hazard = barrier.hazards().getFirst();
            assertEquals("rrOutput", hazard.resource());
            assertEquals(BarrierSynthesis.Mode.WRITE, hazard.before().mode());
            assertEquals(BarrierSynthesis.Mode.READ, hazard.after().mode());
            assertEquals(backend == UpscalerBarrierPlan.Backend.NATIVE
                    ? BarrierSynthesis.Stage.TRANSFER : BarrierSynthesis.Stage.EXTERNAL, hazard.before().stage());
        }
    }

    @Test void nativeBlitDependsOnCurrentColorAndDoesNotClaimSdkHistory() {
        var barrier = UpscalerBarrierPlan.before(UpscalerBarrierPlan.Backend.NATIVE, "produce");
        assertEquals("currentColor", barrier.hazards().getFirst().resource());
        assertEquals(BarrierSynthesis.Stage.TRANSFER, barrier.hazards().getFirst().after().stage());
        assertEquals(2, UpscalerBarrierPlan.operations(UpscalerBarrierPlan.Backend.NATIVE).get(1).uses().size());
    }

    @Test void dlssGuidesAreDistinctReadOnlyInputs() {
        var uses = UpscalerBarrierPlan.operations(UpscalerBarrierPlan.Backend.DLSS_RR).get(1).uses();
        assertEquals(8, uses.size());
        assertEquals(7, uses.stream().filter(u -> u.mode() == BarrierSynthesis.Mode.READ).count());
        assertEquals(uses.size(), uses.stream().map(BarrierSynthesis.Use::resource).distinct().count());
    }

    @Test void flagDefaultsOffAndRequiresGraph() {
        String graph = "engine.renderGraphV2", flag = "engine.upscalerBarriersV2";
        String oldGraph = System.getProperty(graph), oldFlag = System.getProperty(flag);
        try {
            System.clearProperty(flag);
            System.setProperty(graph, "true");
            assertFalse(dev.comfyfluffy.caustica.rewrite.RewriteGates.upscalerBarriersV2());
            System.setProperty(flag, "true");
            assertTrue(dev.comfyfluffy.caustica.rewrite.RewriteGates.upscalerBarriersV2());
            System.setProperty(graph, "false");
            assertFalse(dev.comfyfluffy.caustica.rewrite.RewriteGates.upscalerBarriersV2());
        } finally {
            if (oldGraph == null) System.clearProperty(graph); else System.setProperty(graph, oldGraph);
            if (oldFlag == null) System.clearProperty(flag); else System.setProperty(flag, oldFlag);
        }
    }
}
