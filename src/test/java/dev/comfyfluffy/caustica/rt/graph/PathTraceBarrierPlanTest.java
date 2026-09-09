package dev.comfyfluffy.caustica.rt.graph;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PathTraceBarrierPlanTest {
    @Test void primaryToIndirectDeclaresQueueAndImageDependencies() {
        PathTraceBarrierPlan plan = PathTraceBarrierPlan.create(true, true);
        Set<String> hazards = plan.before(PathTraceBarrierPlan.INDIRECT).hazards().stream()
                .map(BarrierSynthesis.Hazard::resource).collect(Collectors.toSet());
        assertTrue(hazards.contains("continuationQueue"));
        assertTrue(hazards.contains("color"));
        assertTrue(hazards.containsAll(Set.of("normal", "albedo", "specularAlbedo", "viewZ")));
    }

    @Test void exportCoversCurrentImagesAndCrossFrameState() {
        PathTraceBarrierPlan plan = PathTraceBarrierPlan.create(true, true);
        Set<String> hazards = plan.before(PathTraceBarrierPlan.EXPORT).hazards().stream()
                .map(BarrierSynthesis.Hazard::resource).collect(Collectors.toSet());
        assertEquals(Set.of("color", "depth", "motion", "specularMotion", "nrdDiffuse",
                "nrdSpecular", "restirCurrent", "sharcState"), hazards);
        // These were made visible before indirect and remain read-only afterwards.
        assertFalse(hazards.contains("normal"));
        assertFalse(hazards.contains("albedo"));
        assertFalse(hazards.contains("specularAlbedo"));
        assertFalse(hazards.contains("viewZ"));
        assertFalse(hazards.contains("restirPrevious"));
    }

    @Test void optionalSignalsFollowRuntimePath() {
        PathTraceBarrierPlan plain = PathTraceBarrierPlan.create(false, false);
        Set<String> resources = plain.operations().stream().flatMap(operation -> operation.uses().stream())
                .map(BarrierSynthesis.Use::resource).collect(Collectors.toSet());
        assertFalse(resources.contains("viewZ"));
        assertFalse(resources.contains("nrdDiffuse"));
        assertFalse(resources.contains("nrdSpecular"));
    }

    @Test void previousAndCurrentRestirRemainDistinct() {
        var indirect = PathTraceBarrierPlan.create(true, false).operations().get(1);
        assertTrue(indirect.uses().stream().anyMatch(use -> use.resource().equals("restirPrevious")
                && use.mode() == BarrierSynthesis.Mode.READ));
        assertTrue(indirect.uses().stream().anyMatch(use -> use.resource().equals("restirCurrent")
                && use.mode() == BarrierSynthesis.Mode.WRITE));
    }


    @Test void generatedScopeMatchesLegacyBarrier() {
        assertEquals(PostImageBarriers.STAGES, PathTraceBarriers.STAGES);
        assertEquals(PostImageBarriers.ACCESS, PathTraceBarriers.ACCESS);
    }

}
