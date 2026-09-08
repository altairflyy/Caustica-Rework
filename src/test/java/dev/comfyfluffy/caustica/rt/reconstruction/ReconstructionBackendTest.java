package dev.comfyfluffy.caustica.rt.reconstruction;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReconstructionBackendTest {
    @Test
    void contractHasBackendSpecificInputAndRequiredLifecycle() throws Exception {
        List<String> calls = new ArrayList<>();
        ReconstructionBackend<String> backend = new ReconstructionBackend<>() {
            @Override
            public boolean available() {
                calls.add("available");
                return true;
            }

            @Override
            public void requestReset() {
                calls.add("reset");
            }

            @Override
            public ReconstructionResult execute(String input) {
                calls.add("execute:" + input);
                return null;
            }

            @Override
            public void destroy() {
                calls.add("destroy");
            }
        };

        backend.available();
        backend.requestReset();
        assertNull(backend.execute("svgf-specific-request"));
        backend.destroy();

        assertEquals(List.of("available", "reset", "execute:svgf-specific-request", "destroy"), calls);
        Method execute = ReconstructionBackend.class.getMethod("execute", Object.class);
        assertEquals(ReconstructionResult.class, execute.getReturnType());
    }
}
