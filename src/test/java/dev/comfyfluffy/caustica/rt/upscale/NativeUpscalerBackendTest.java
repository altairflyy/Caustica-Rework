package dev.comfyfluffy.caustica.rt.upscale;

import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeUpscalerBackendTest {
    @Test
    void nativeBackendIsAlwaysAvailableAndUsesDisplayExtent() {
        NativeUpscalerBackend backend = new NativeUpscalerBackend();

        assertTrue(backend.available());
        assertEquals(new FrameContext.Extent(2560, 1440), backend.recommendedRenderExtent(2560, 1440));
        backend.requestReset();
        backend.destroy();
    }
}
