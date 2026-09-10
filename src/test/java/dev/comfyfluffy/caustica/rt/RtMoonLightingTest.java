package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtMoonLightingTest {
    @Test
    void moonLitFractionMapsFullMoonToMaxAndNewMoonToMin() {
        // Phase 0: Full moon -> maximum lit fraction (1.0)
        assertEquals(1.0f, RtComposite.moonLitFraction(0.0f), 1e-6f);

        // Phase 2: Third quarter -> intermediate lit fraction (0.5)
        assertEquals(0.5f, RtComposite.moonLitFraction(2.0f), 1e-6f);

        // Phase 4: New moon -> minimum lit fraction (0.0)
        assertEquals(0.0f, RtComposite.moonLitFraction(4.0f), 1e-6f);

        // Phase 6: First quarter -> intermediate lit fraction (0.5)
        assertEquals(0.5f, RtComposite.moonLitFraction(6.0f), 1e-6f);
    }

    @Test
    void moonLitFractionIsSymmetricAroundNewMoon() {
        for (float delta = 0.0f; delta <= 4.0f; delta += 0.25f) {
            float beforeNew = RtComposite.moonLitFraction(4.0f - delta);
            float afterNew = RtComposite.moonLitFraction(4.0f + delta);
            assertEquals(beforeNew, afterNew, 1e-6f, "Symmetry violated at delta " + delta);
        }
    }

    @Test
    void moonLitFractionDecreasesMonotonicallyFromFullToNewMoon() {
        float prev = RtComposite.moonLitFraction(0.0f);
        for (float phase = 0.5f; phase <= 4.0f; phase += 0.5f) {
            float current = RtComposite.moonLitFraction(phase);
            assertTrue(current < prev, "Expected strictly decreasing litFraction from phase " + (phase - 0.5f) + " to " + phase);
            prev = current;
        }
    }

    @Test
    void moonLitFractionIncreasesMonotonicallyFromNewToFullMoon() {
        float prev = RtComposite.moonLitFraction(4.0f);
        for (float phase = 4.5f; phase <= 8.0f; phase += 0.5f) {
            float current = RtComposite.moonLitFraction(phase);
            assertTrue(current > prev, "Expected strictly increasing litFraction from phase " + (phase - 0.5f) + " to " + phase);
            prev = current;
        }
    }

    @Test
    void moonPeakRadiancePreservesEnvelope() {
        // Full moon should produce maximum peak radiance (0.20)
        float fullLit = RtComposite.moonLitFraction(0.0f);
        float fullPeak = 0.20f * (0.15f + 0.85f * fullLit);
        assertEquals(0.20f, fullPeak, 1e-6f);

        // New moon should produce minimal base radiance (0.03 = 0.20 * 0.15)
        float newLit = RtComposite.moonLitFraction(4.0f);
        float newPeak = 0.20f * (0.15f + 0.85f * newLit);
        assertEquals(0.03f, newPeak, 1e-6f);
    }
}
