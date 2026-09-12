package dev.comfyfluffy.caustica.rt.accel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RtAccelTlasModeTest {
    @Test
    void firstUseBuilds() {
        assertEquals(RtAccel.TlasMode.BUILD, RtAccel.selectTlasMode(false, 0, 128, false));
    }

    @Test
    void stableCountUpdates() {
        assertEquals(RtAccel.TlasMode.UPDATE, RtAccel.selectTlasMode(true, 128, 128, false));
    }

    @Test
    void countChangeBuilds() {
        assertEquals(RtAccel.TlasMode.BUILD, RtAccel.selectTlasMode(true, 128, 129, false));
    }

    @Test
    void resizeBuilds() {
        assertEquals(RtAccel.TlasMode.BUILD, RtAccel.selectTlasMode(true, 128, 128, true));
    }

    @Test
    void laterStableCountUpdatesAgain() {
        assertEquals(RtAccel.TlasMode.UPDATE, RtAccel.selectTlasMode(true, 256, 256, false));
    }
}
