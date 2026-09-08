package dev.comfyfluffy.caustica.rt.upscale;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XessUpscalerBackendTest {
    private static final Path REPO_ROOT = repoRoot();

    @Test
    void adapterPreservesCapabilityGateInputsJitterAndReset() throws IOException {
        String backend = Files.readString(REPO_ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/upscale/XessUpscalerBackend.java"));
        String delegate = Files.readString(REPO_ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtXessUpscaler.java"));
        String bringup = Files.readString(REPO_ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtDeviceBringup.java"));

        assertTrue(backend.contains("return RtXessUpscaler.enabled();"));
        assertTrue(backend.contains("input.jitterAlreadyIntegrated() ? 0.0f : input.jitterX()"));
        assertTrue(backend.contains("input.onTeleportReset().run();"));
        assertTrue(delegate.contains("INIT_FLAGS = FLAG_INVERTED_DEPTH"));
        assertTrue(bringup.contains("xessFeaturesEnabled = support.xess;"));
    }

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root not found");
        return path;
    }
}
