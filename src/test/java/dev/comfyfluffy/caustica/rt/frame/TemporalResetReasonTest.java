package dev.comfyfluffy.caustica.rt.frame;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalResetReasonTest {
    @Test
    void everyReasonHasAUniqueBitAndCanBeCombined() {
        int reasons = TemporalResetReason.none();
        for (TemporalResetReason reason : TemporalResetReason.values()) {
            reasons = TemporalResetReason.add(reasons, reason);
        }

        assertEquals(TemporalResetReason.values().length,
                Arrays.stream(TemporalResetReason.values()).mapToInt(TemporalResetReason::bit).distinct().count());
        for (TemporalResetReason reason : TemporalResetReason.values()) {
            assertTrue(TemporalResetReason.contains(reasons, reason));
        }
    }

    @Test
    void anUnrelatedReasonIsNotReported() {
        int reasons = TemporalResetReason.of(TemporalResetReason.RESOURCE_RELOAD);
        assertTrue(TemporalResetReason.contains(reasons, TemporalResetReason.RESOURCE_RELOAD));
        assertFalse(TemporalResetReason.contains(reasons, TemporalResetReason.MANUAL));
    }
}
