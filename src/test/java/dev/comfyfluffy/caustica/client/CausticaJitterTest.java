package dev.comfyfluffy.caustica.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CausticaJitterTest {
    @Test
    void normalSequenceMatchesPreOverflowBehavior() {
        int phaseCount = 32;
        CausticaJitter jitter = new CausticaJitter(0);
        for (int frame = 0; frame < 128; frame++) {
            int expected = (frame % phaseCount) + 1;
            int actual = jitter.advanceIndex(phaseCount);
            assertEquals(expected, actual, "Mismatch at normal frame " + frame);
            assertTrue(jitter.phaseIndex() >= 0 && jitter.phaseIndex() < phaseCount);
        }
    }

    @Test
    void exactCyclicSequencePreservedForRequiredPhaseCounts() {
        int[] phases = {3, 5, 7, 10, 15, 32};
        for (int phaseCount : phases) {
            CausticaJitter jitter = new CausticaJitter(0);
            int prev = jitter.advanceIndex(phaseCount);
            assertEquals(1, prev);

            for (int step = 1; step < phaseCount * 4; step++) {
                int next = jitter.advanceIndex(phaseCount);
                int expected = (prev % phaseCount) + 1;
                assertEquals(expected, next, "Cycle broken for phaseCount " + phaseCount + " at step " + step);
                assertTrue(jitter.phaseIndex() >= 0 && jitter.phaseIndex() < phaseCount);
                prev = next;
            }
        }
    }

    @Test
    void boundedCounterDoesNotOverflowAcrossManyFrames() {
        CausticaJitter jitter = new CausticaJitter(0);
        int phaseCount = 37;
        for (int i = 0; i < 5000; i++) {
            int idx = jitter.advanceIndex(phaseCount);
            assertTrue(idx >= 1 && idx <= phaseCount);
            assertTrue(jitter.phaseIndex() >= 0 && jitter.phaseIndex() < phaseCount);
        }
    }

    @Test
    void initialLargeOrNegativeStateSafelyNormalizesWithoutBreakingCycle() {
        int[] phases = {3, 5, 7, 10, 15, 32};
        for (int phaseCount : phases) {
            // Start from Integer.MAX_VALUE
            CausticaJitter jitterMax = new CausticaJitter(Integer.MAX_VALUE);
            int idx1 = jitterMax.advanceIndex(phaseCount);
            int idx2 = jitterMax.advanceIndex(phaseCount);
            assertEquals((idx1 % phaseCount) + 1, idx2, "Cycle broken after Integer.MAX_VALUE for phase " + phaseCount);
            assertTrue(jitterMax.phaseIndex() >= 0 && jitterMax.phaseIndex() < phaseCount);

            // Start from Integer.MIN_VALUE
            CausticaJitter jitterMin = new CausticaJitter(Integer.MIN_VALUE);
            int minIdx1 = jitterMin.advanceIndex(phaseCount);
            int minIdx2 = jitterMin.advanceIndex(phaseCount);
            assertEquals((minIdx1 % phaseCount) + 1, minIdx2, "Cycle broken after Integer.MIN_VALUE for phase " + phaseCount);
            assertTrue(jitterMin.phaseIndex() >= 0 && jitterMin.phaseIndex() < phaseCount);
        }
    }

    @Test
    void dynamicPhaseCountChangesSafelyNormalizesState() {
        CausticaJitter jitter = new CausticaJitter(0);
        // Advance 29 steps in 32-phase
        for (int i = 0; i < 29; i++) {
            jitter.advanceIndex(32);
        }
        assertEquals(29, jitter.phaseIndex());

        // Switch to phaseCount = 5: state 29 normalizes to 29 % 5 = 4 -> index 5, next phaseIndex 0
        int idx = jitter.advanceIndex(5);
        assertEquals(5, idx);
        assertEquals(0, jitter.phaseIndex());

        // Next step in phaseCount = 5 gives index 1
        assertEquals(1, jitter.advanceIndex(5));
        assertEquals(1, jitter.phaseIndex());

        // Switch to phaseCount = 3: state 1 normalizes to 1 % 3 = 1 -> index 2
        assertEquals(2, jitter.advanceIndex(3));
        assertEquals(2, jitter.phaseIndex());
        assertEquals(3, jitter.advanceIndex(3));
        assertEquals(0, jitter.phaseIndex());
    }

    @Test
    void prepareProducesFiniteNonDegenerateCoordinates() {
        CausticaJitter jitter = new CausticaJitter(Integer.MAX_VALUE);
        for (int i = 0; i < 64; i++) {
            jitter.prepareXess(); // phaseCount 32

            float x = jitter.jitterPixelsX();
            float y = jitter.jitterPixelsY();

            assertTrue(Float.isFinite(x), "Jitter X must be finite");
            assertTrue(Float.isFinite(y), "Jitter Y must be finite");
            assertTrue(x > -0.5f && x < 0.5f, "Jitter X must be in (-0.5, 0.5), got " + x);
            assertTrue(y > -0.5f && y < 0.5f, "Jitter Y must be in (-0.5, 0.5), got " + y);
            assertFalse(x == -0.5f && y == -0.5f, "Jitter must not freeze at degenerate (-0.5, -0.5)");
        }
    }

    @Test
    void provesRawFloorModOverflowDefectForNonPowersOfTwo() {
        // Demonstration of why unbounded counter with Math.floorMod fails across Integer.MAX_VALUE -> MIN_VALUE:
        // For phaseCount = 3: 2147483647 % 3 == 1 (index 2), and -2147483648 % 3 == 1 (index 2, duplicate!)
        int oldMaxIdx3 = Math.floorMod(Integer.MAX_VALUE, 3) + 1;
        int oldMinIdx3 = Math.floorMod(Integer.MIN_VALUE, 3) + 1;
        assertEquals(oldMaxIdx3, oldMinIdx3, "Raw floorMod duplicates phase across wrap for phaseCount 3");

        // For phaseCount = 10: 2147483647 % 10 == 7 (index 8), and -2147483648 % 10 == 2 (index 3, skips 9, 10, 1, 2!)
        int oldMaxIdx10 = Math.floorMod(Integer.MAX_VALUE, 10) + 1;
        int oldMinIdx10 = Math.floorMod(Integer.MIN_VALUE, 10) + 1;
        assertNotEquals((oldMaxIdx10 % 10) + 1, oldMinIdx10, "Raw floorMod skips phases across wrap for phaseCount 10");

        // In contrast, the bounded counter advanceIndex maintains the exact sequence:
        CausticaJitter bounded3 = new CausticaJitter(Integer.MAX_VALUE);
        int b1_3 = bounded3.advanceIndex(3);
        int b2_3 = bounded3.advanceIndex(3);
        assertEquals((b1_3 % 3) + 1, b2_3, "Bounded counter advances correctly across initial large state for phaseCount 3");

        CausticaJitter bounded10 = new CausticaJitter(Integer.MAX_VALUE);
        int b1_10 = bounded10.advanceIndex(10);
        int b2_10 = bounded10.advanceIndex(10);
        assertEquals((b1_10 % 10) + 1, b2_10, "Bounded counter advances correctly across initial large state for phaseCount 10");
    }
}
