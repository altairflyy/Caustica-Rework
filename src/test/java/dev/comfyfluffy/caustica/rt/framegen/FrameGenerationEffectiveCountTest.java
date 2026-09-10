package dev.comfyfluffy.caustica.rt.framegen;

import dev.comfyfluffy.caustica.rt.RtFramePresenter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameGenerationEffectiveCountTest {
    @Test
    void effectiveCountAndUniformFractionsWhenRequestedMatchesAvailable() {
        // requested=3, swapchain=4 -> effective=3 (want == generatedCount)
        int effective = RtFramePresenter.effectiveGeneratedCount(3, 4);
        assertEquals(3, effective);

        // Indices 1, 2, 3 should produce uniform fractions: 1/4 (0.25), 2/4 (0.50), 3/4 (0.75)
        assertEquals(0.25f, RtFramePresenter.interpolationFraction(1, effective), 1e-6f);
        assertEquals(0.50f, RtFramePresenter.interpolationFraction(2, effective), 1e-6f);
        assertEquals(0.75f, RtFramePresenter.interpolationFraction(3, effective), 1e-6f);
    }

    @Test
    void effectiveCountAndUniformFractionsWhenConstrainedFromThreeToTwo() {
        // requested=3, swapchain=3 -> effective=2
        int effective = RtFramePresenter.effectiveGeneratedCount(3, 3);
        assertEquals(2, effective);

        // Indices 1, 2 should produce uniform fractions: 1/3 (~0.33333334), 2/3 (~0.6666667)
        // (Previously, index 1 was 0.25 and index 2 was 0.50, creating a non-uniform temporal gap)
        assertEquals(1.0f / 3.0f, RtFramePresenter.interpolationFraction(1, effective), 1e-6f);
        assertEquals(2.0f / 3.0f, RtFramePresenter.interpolationFraction(2, effective), 1e-6f);
    }

    @Test
    void effectiveCountAndUniformFractionsWhenConstrainedFromTwoToOne() {
        // requested=2, swapchain=2 -> effective=1
        int effective = RtFramePresenter.effectiveGeneratedCount(2, 2);
        assertEquals(1, effective);

        // Index 1 should produce uniform fraction: 1/2 (0.50)
        assertEquals(0.50f, RtFramePresenter.interpolationFraction(1, effective), 1e-6f);
    }

    @Test
    void effectiveCountZeroWhenSwapchainCannotLendExtraImages() {
        assertEquals(0, RtFramePresenter.effectiveGeneratedCount(3, 1));
        assertEquals(0, RtFramePresenter.effectiveGeneratedCount(3, 0));
        assertEquals(0, RtFramePresenter.effectiveGeneratedCount(0, 4));
        assertEquals(0, RtFramePresenter.effectiveGeneratedCount(-1, 4));
    }
}
