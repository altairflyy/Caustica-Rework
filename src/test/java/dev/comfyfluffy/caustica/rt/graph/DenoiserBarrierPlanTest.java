package dev.comfyfluffy.caustica.rt.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DenoiserBarrierPlanTest {
    @Test void pingPongPlansKeepPreviousReadOnlyAndCurrentWritable() {
        for (boolean writeToPing : List.of(false, true)) {
            DenoiserBarrierPlan plan = DenoiserBarrierPlan.svgf(writeToPing, 5, 1);
            var reproject = plan.operations().stream()
                    .filter(op -> op.name().equals(DenoiserBarrierPlan.REPROJECT)).findFirst().orElseThrow();
            String previousHistory = writeToPing ? "historyPong" : "historyPing";
            String currentHistory = writeToPing ? "historyPing" : "historyPong";
            assertTrue(reproject.uses().stream().anyMatch(use -> use.resource().equals(previousHistory)
                    && use.mode() == BarrierSynthesis.Mode.READ));
            assertFalse(reproject.uses().stream().anyMatch(use -> use.resource().equals(previousHistory)
                    && use.mode() != BarrierSynthesis.Mode.READ));
            assertTrue(reproject.uses().stream().anyMatch(use -> use.resource().equals(currentHistory)
                    && use.mode() == BarrierSynthesis.Mode.WRITE));
            assertTrue(plan.before(DenoiserBarrierPlan.REPROJECT).required());
            assertTrue(plan.before(DenoiserBarrierPlan.EXPORT).required());
        }
    }

    @Test void everyIntraFrameSvgfBoundaryHasACompiledHazard() {
        DenoiserBarrierPlan plan = DenoiserBarrierPlan.svgf(true, 5, 1);
        for (String operation : List.of(DenoiserBarrierPlan.REPROJECT,
                DenoiserBarrierPlan.atrous(0), DenoiserBarrierPlan.atrous(1),
                DenoiserBarrierPlan.HISTORY_FEEDBACK, DenoiserBarrierPlan.atrous(2),
                DenoiserBarrierPlan.atrous(3), DenoiserBarrierPlan.atrous(4),
                DenoiserBarrierPlan.PREVIOUS_GUIDES, DenoiserBarrierPlan.EXPORT)) {
            assertTrue(plan.before(operation).required(), operation);
        }
    }

    @Test void previousGuidesAreCrossFrameInputsAndCurrentFrameOutputs() {
        DenoiserBarrierPlan plan = DenoiserBarrierPlan.svgf(true, 5, 1);
        var reproject = plan.operations().get(1);
        assertTrue(reproject.uses().stream().anyMatch(use -> use.resource().equals("previousViewZ")
                && use.mode() == BarrierSynthesis.Mode.READ));
        var update = plan.operations().stream().filter(op -> op.name().equals(DenoiserBarrierPlan.PREVIOUS_GUIDES))
                .findFirst().orElseThrow();
        assertTrue(update.uses().stream().anyMatch(use -> use.resource().equals("previousViewZ")
                && use.mode() == BarrierSynthesis.Mode.WRITE));
    }

    @Test void nrdExternalOutputsFeedCombineAndExport() {
        DenoiserBarrierPlan plan = DenoiserBarrierPlan.nrd();
        assertEquals(List.of("externalNrd", "nrdCombine", "nrdExport"),
                plan.operations().stream().map(BarrierSynthesis.Operation::name).toList());
        assertTrue(plan.before(DenoiserBarrierPlan.NRD_COMBINE).required());
        assertTrue(plan.before(DenoiserBarrierPlan.NRD_EXPORT).required());
    }

    @Test void denoiserFlagRequiresGraphAndDefaultsOff() {
        String graph = dev.comfyfluffy.caustica.rewrite.RewriteGates.RENDER_GRAPH_V2_KEY;
        String denoiser = dev.comfyfluffy.caustica.rewrite.RewriteGates.DENOISER_BARRIERS_V2_KEY;
        String oldGraph = System.getProperty(graph), oldDenoiser = System.getProperty(denoiser);
        try {
            System.clearProperty(graph); System.clearProperty(denoiser);
            assertFalse(dev.comfyfluffy.caustica.rewrite.RewriteGates.denoiserBarriersV2());
            for (boolean g : List.of(false, true)) for (boolean d : List.of(false, true)) {
                System.setProperty(graph, Boolean.toString(g));
                System.setProperty(denoiser, Boolean.toString(d));
                assertEquals(g && d, dev.comfyfluffy.caustica.rewrite.RewriteGates.denoiserBarriersV2());
            }
        } finally {
            restore(graph, oldGraph); restore(denoiser, oldDenoiser);
        }
    }

    @Test void generatedScopeMatchesLegacyBarrier() {
        assertEquals(PostImageBarriers.STAGES, DenoiserBarriers.STAGES);
        assertEquals(PostImageBarriers.ACCESS, DenoiserBarriers.ACCESS);
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
}
