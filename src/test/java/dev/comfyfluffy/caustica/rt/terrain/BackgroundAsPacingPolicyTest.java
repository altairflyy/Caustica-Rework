package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BackgroundAsPacingPolicyTest {
    @Test
    void entersBusyAboveThreshold() {
        var policy = new BackgroundAsPacingPolicy();
        assertEquals(BackgroundAsPacingPolicy.State.GPU_BUSY,
                policy.update(14.0, 1L, 14.0, 11.0, 1_000L).state());
    }

    @Test
    void hysteresisRequiresStableLowerCost() {
        var policy = new BackgroundAsPacingPolicy();
        policy.update(15.0, 1L, 14.0, 11.0, 1_000L);
        assertEquals(BackgroundAsPacingPolicy.State.GPU_BUSY,
                policy.update(10.0, 100L, 14.0, 11.0, 1_000L).state());
        assertEquals(BackgroundAsPacingPolicy.State.NORMAL,
                policy.update(10.0, 1_101L, 14.0, 11.0, 1_000L).state());
    }

    @Test
    void remainsBusyWhileCostIsHigh() {
        var policy = new BackgroundAsPacingPolicy();
        policy.update(15.0, 1L, 14.0, 11.0, 1_000L);
        assertEquals(BackgroundAsPacingPolicy.State.GPU_BUSY,
                policy.update(12.0, 5_000L, 14.0, 11.0, 1_000L).state());
    }
}
