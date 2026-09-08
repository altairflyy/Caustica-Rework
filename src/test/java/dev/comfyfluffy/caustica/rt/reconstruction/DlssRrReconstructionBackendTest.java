package dev.comfyfluffy.caustica.rt.reconstruction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DlssRrReconstructionBackendTest {
    private static final Path REPO_ROOT = repoRoot();

    @Test
    void adapterPreservesFeatureSetupGuidesAndFailureResult() throws IOException {
        String source = Files.readString(REPO_ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/rt/reconstruction/DlssRrReconstructionBackend.java"));

        assertTrue(source.contains("delegate.ensureFeature(input.command().address()"));
        assertTrue(source.contains("input.color(), input.depth(), input.motion()"));
        assertTrue(source.contains("input.diffuseAlbedo(), input.specularAlbedo(), input.normals()"));
        assertTrue(source.contains("input.specularMotion()"));
        assertTrue(source.contains("completed ? input.output() : input.color()"));
        assertTrue(source.contains("delegate.requestReset();"));
    }

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("repository root not found");
        }
        return path;
    }
}
