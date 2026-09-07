package dev.comfyfluffy.caustica.rt.device;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuCapabilitiesTest {
    @Test
    void capabilitiesAreAnImmutableReadOnlyRecord() {
        GpuCapabilities capabilities = new GpuCapabilities(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                8.0f,
                4,
                12,
                "Example GPU",
                "Example Vendor",
                true
        );

        assertTrue(GpuCapabilities.class.isRecord());
        assertTrue(Modifier.isFinal(GpuCapabilities.class.getModifiers()));

        assertEquals(true, capabilities.rtRequested());
        assertEquals(true, capabilities.serExtEnabled());
        assertEquals(true, capabilities.ommEnabled());
        assertEquals(true, capabilities.reflexEnabled());
        assertEquals(true, capabilities.presentIdEnabled());
        assertEquals(true, capabilities.wideLinesEnabled());
        assertEquals(true, capabilities.xessFeaturesEnabled());
        assertEquals(8.0f, capabilities.maxLineWidth());
        assertEquals(4, capabilities.overlayMsaaSamples());
        assertEquals(12, capabilities.maxOpacity4StateSubdivisionLevel());
        assertEquals("Example GPU", capabilities.gpuName());
        assertEquals("Example Vendor", capabilities.gpuVendorName());
        assertEquals(true, capabilities.looksLikeRtxFrameGenerationSeries());

        assertEquals(
                13,
                Arrays.stream(GpuCapabilities.class.getRecordComponents()).count()
        );
    }
}
