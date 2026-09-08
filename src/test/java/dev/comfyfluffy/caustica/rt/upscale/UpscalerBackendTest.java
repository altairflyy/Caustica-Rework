package dev.comfyfluffy.caustica.rt.upscale;

import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UpscalerBackendTest {
    @Test
    void contractHasBackendSpecificInputExtentAndRequiredLifecycle() throws Exception {
        List<String> calls = new ArrayList<>();
        UpscalerBackend<String> backend = new UpscalerBackend<>() {
            @Override
            public boolean available() {
                calls.add("available");
                return true;
            }

            @Override
            public FrameContext.Extent recommendedRenderExtent(int width, int height) {
                calls.add("extent");
                return new FrameContext.Extent(width, height);
            }

            @Override
            public void requestReset() {
                calls.add("reset");
            }

            @Override
            public UpscaleResult execute(String input) {
                calls.add("execute:" + input);
                return null;
            }

            @Override
            public void destroy() {
                calls.add("destroy");
            }
        };

        backend.available();
        assertEquals(new FrameContext.Extent(1920, 1080), backend.recommendedRenderExtent(1920, 1080));
        backend.requestReset();
        assertNull(backend.execute("fsr-specific-request"));
        backend.destroy();

        assertEquals(List.of("available", "extent", "reset", "execute:fsr-specific-request", "destroy"), calls);
        Method execute = UpscalerBackend.class.getMethod("execute", Object.class);
        assertEquals(UpscaleResult.class, execute.getReturnType());
    }
}
