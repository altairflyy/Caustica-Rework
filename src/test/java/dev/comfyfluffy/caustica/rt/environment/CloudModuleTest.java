package dev.comfyfluffy.caustica.rt.environment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudModuleTest {
    @Test
    void anchorWrapPreservesTheLegacyExactPeriod() {
        assertEquals(0f, CloudModule.wrapAnchor(0.0), 0f);
        assertEquals(0f, CloudModule.wrapAnchor(24576.0), 0f);
        assertEquals(24575f, CloudModule.wrapAnchor(-1.0), 0f);
        assertEquals(17.5f, CloudModule.wrapAnchor(24576.0 + 17.5), 0f);
    }

    @Test
    void viewLimitKeepsTheLegacyFloorAndHeightScaling() {
        assertEquals(3072f, CloudModule.viewLimit(0f), 0f);
        assertEquals(3072f, CloudModule.viewLimit(-100f), 0f);
        assertEquals(6000f, CloudModule.viewLimit(1000f), 0f);
    }

    @Test
    void cloudColorUsesTheLegacyStandardSrgbDecode() {
        assertEquals(0f, CloudModule.srgb8ToLinear(0), 0f);
        assertEquals(1f, CloudModule.srgb8ToLinear(255), 1.0e-6f);
        assertEquals(0.2158605f, CloudModule.srgb8ToLinear(128), 1.0e-6f);
    }
}
