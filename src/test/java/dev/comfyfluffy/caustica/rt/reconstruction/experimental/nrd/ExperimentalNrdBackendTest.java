package dev.comfyfluffy.caustica.rt.reconstruction.experimental.nrd;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentalNrdBackendTest {
    @Test
    void retiredBackendCannotBeSelected() {
        assertFalse(new ExperimentalNrdBackend().selected());
    }

    @Test
    void compositeCanReachNrdOnlyThroughTheQuarantineBoundary() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java"));

        assertTrue(source.contains("private final ExperimentalNrdBackend nrdBackend"));
        assertTrue(source.contains("nrdBackend.selected()"));
        assertFalse(source.contains("RtNrdDenoiser"));
    }
}
