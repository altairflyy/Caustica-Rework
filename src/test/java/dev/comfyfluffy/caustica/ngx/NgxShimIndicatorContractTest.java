package dev.comfyfluffy.caustica.ngx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NgxShimIndicatorContractTest {
    @Test
    void dlssDiagnosticIndicatorCompensatesForPresentationYFlip() throws IOException {
        String source = Files.readString(Path.of("native", "ngx_shim", "ngx_shim.cpp"))
                .replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.split("eval\\.InIndicatorInvertYAxis = 1;", -1).length - 1 == 2,
                "both DLSS-SR and DLSS-RR must counter-flip the registry-enabled NVIDIA indicator");
    }
}
