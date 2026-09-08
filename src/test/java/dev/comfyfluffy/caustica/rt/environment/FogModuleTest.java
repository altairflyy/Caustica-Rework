package dev.comfyfluffy.caustica.rt.environment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FogModuleTest {
    @Test
    void usesTheSameStandardSrgbDecodeAsTheLegacyBindings() {
        assertEquals(0f, FogModule.srgb8ToLinear(0), 0f);
        assertEquals(1f, FogModule.srgb8ToLinear(255), 1.0e-6f);
        assertEquals(0.2158605f, FogModule.srgb8ToLinear(128), 1.0e-6f);
    }
}
