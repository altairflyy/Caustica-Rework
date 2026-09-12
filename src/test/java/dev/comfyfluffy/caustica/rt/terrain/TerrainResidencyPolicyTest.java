package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainResidencyPolicyTest {
    @Test
    void entersPressureBelowHighHeadroomAndStopsDispatch() {
        TerrainResidencyPolicy policy = new TerrainResidencyPolicy();
        TerrainResidencyPolicy.Decision decision = policy.update(900, 1000, 200, 50);
        assertEquals(TerrainResidencyPolicy.State.PRESSURE, decision.state());
        assertFalse(decision.dispatchAllowed());
    }

    @Test
    void hysteresisPreventsImmediateResume() {
        TerrainResidencyPolicy policy = new TerrainResidencyPolicy();
        policy.update(900, 1000, 200, 50);
        assertEquals(TerrainResidencyPolicy.State.PRESSURE, policy.update(810, 1000, 200, 50).state());
        assertEquals(TerrainResidencyPolicy.State.NORMAL, policy.update(740, 1000, 200, 50).state());
        assertTrue(policy.update(740, 1000, 200, 50).dispatchAllowed());
    }

    @Test
    void distanceOrderingIsDeterministic() {
        long near = TerrainResidencyPolicy.distanceSquared(1, 0, 0, 0, 0, 0);
        long far = TerrainResidencyPolicy.distanceSquared(3, 0, 0, 0, 0, 0);
        assertTrue(near < far);
    }
}
