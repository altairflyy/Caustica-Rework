package dev.comfyfluffy.caustica.rt.upscale;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FsrUpscalerBackendTest {
    private static final Path REPO_ROOT = repoRoot();

    @Test
    void adapterPreservesFidelityFxInputsResetAndReversedZDelegate() throws IOException {
        String backend = Files.readString(REPO_ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/upscale/FsrUpscalerBackend.java"));
        String delegate = Files.readString(REPO_ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtFsrUpscaler.java"));

        assertTrue(backend.contains("delegate.evaluate(input.command().address(), input.source(), input.depth(), input.motion()"));
        assertTrue(backend.contains("null, input.output()"));
        assertTrue(backend.contains("input.jitterX(), input.jitterY(), input.fovY()"));
        assertTrue(backend.contains("input.onTeleportReset().run();"));
        assertTrue(delegate.contains("FLAG_DEPTH_INVERTED") && delegate.contains("FLAG_DEPTH_INFINITE"));
        assertTrue(delegate.contains("Float.MAX_VALUE, CAMERA_NEAR, fovY"));
    }

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root not found");
        return path;
    }
}
