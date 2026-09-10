package dev.comfyfluffy.caustica.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CausticaJitterTest {
    @Test
    void normalSequenceMatchesPreOverflowBehavior() {
        int phaseCount = 32;
        for (int frame = 0; frame < 128; frame++) {
            int expected = (frame % phaseCount) + 1;
            int actual = CausticaJitter.computeIndex(frame, phaseCount);
            assertEquals(expected, actual, "Mismatch at normal frame " + frame);
        }
    }

    @Test
    void integerOverflowPreservesPositiveBoundedHaltonIndex() {
        int[] testPhases = {32, 17, 45, 64};
        int[] testOffsets = {-3, -2, -1, 0, 1, 2, 3};

        for (int phaseCount : testPhases) {
            for (int offset : testOffsets) {
                int frame = Integer.MAX_VALUE + offset; // Wraps across Integer.MAX_VALUE to Integer.MIN_VALUE
                int index = CausticaJitter.computeIndex(frame, phaseCount);

                assertTrue(index >= 1, "Index must be >= 1 (non-degenerate) at frame " + frame + ": was " + index);
                assertTrue(index <= phaseCount, "Index must be <= phaseCount at frame " + frame + ": was " + index);
            }
        }
    }

    @Test
    void prepareTransitionsSmoothlyAcrossIntegerMaxWrapWithoutDegeneracy() {
        // Start 2 frames before Integer.MAX_VALUE
        CausticaJitter jitter = new CausticaJitter(Integer.MAX_VALUE - 2);

        for (int i = 0; i < 6; i++) {
            jitter.prepareXess(); // phaseCount 32

            float x = jitter.jitterPixelsX();
            float y = jitter.jitterPixelsY();

            // Halton coords must be strictly within (-0.5, 0.5) and finite
            assertTrue(Float.isFinite(x), "Jitter X must be finite");
            assertTrue(Float.isFinite(y), "Jitter Y must be finite");
            assertTrue(x > -0.5f && x < 0.5f, "Jitter X must be in (-0.5, 0.5), got " + x);
            assertTrue(y > -0.5f && y < 0.5f, "Jitter Y must be in (-0.5, 0.5), got " + y);

            // The bug previously returned 0.0f from halton() for negative indices, giving exactly -0.5f
            assertFalse(x == -0.5f && y == -0.5f, "Jitter must not freeze at degenerate (-0.5, -0.5)");
        }
    }

    @Test
    void powerOfTwoPhaseSequenceIsSeamlessAcrossMaxWrap() {
        int phaseCount = 32;
        int maxIndex = CausticaJitter.computeIndex(Integer.MAX_VALUE, phaseCount);
        int wrappedIndex = CausticaJitter.computeIndex(Integer.MIN_VALUE, phaseCount);

        // Integer.MAX_VALUE = 2147483647 = 32 * 67108863 + 31 -> index 32
        assertEquals(32, maxIndex);
        // Integer.MIN_VALUE = -2147483648 = 32 * (-67108864) + 0 -> index 1
        assertEquals(1, wrappedIndex);
    }
}
