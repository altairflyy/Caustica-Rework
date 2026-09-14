package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtTonemapShaderContractTest {
    private static final Path REPO_ROOT = repoRoot();

    @Test
    void sdrAndHdrUseTheSameSelectedToneCurveAndLook() throws IOException {
        String shader = Files.readString(REPO_ROOT.resolve("shaders/display/display.comp"));

        assertTrue(shader.contains("vec3 mapToneCurve(vec3 c)"));
        assertTrue(shader.contains("return applyLook(mapToneCurve(c));"),
                "SDR must apply the selected curve and final look");
        assertTrue(shader.contains("vec3 curveAtPaperWhite = max(mapToneCurve(vec3(1.0 / range))"),
                "HDR must normalize the selected curve at paper white");
        assertTrue(shader.contains("vec3 paperReferred = mapToneCurve(c / range) / curveAtPaperWhite;"),
                "HDR must apply the selected curve to scene radiance");
        assertTrue(shader.contains("paperReferred = applyLook(clamp(paperReferred / range"),
                "HDR must apply gamma, saturation and contrast too");
    }

    @Test
    void javaAndShaderKeepTheSameSixOperatorIndices() throws IOException {
        String config = Files.readString(REPO_ROOT.resolve(
                "src/main/java/dev/comfyfluffy/caustica/CausticaConfig.java"));
        String shader = Files.readString(REPO_ROOT.resolve("shaders/display/display.comp"));

        assertTrue(config.contains("case \"pbr_neutral\" -> 1;"));
        assertTrue(config.contains("case \"aces\" -> 2;"));
        assertTrue(config.contains("case \"filmic\" -> 3;"));
        assertTrue(config.contains("case \"linear\" -> 4;"));
        assertTrue(config.contains("case \"psychov\", \"psycho_v\", \"psychovisual\", \"psycho\" -> 5;"));
        assertTrue(shader.contains("0 AgX, 1 PBR Neutral, 2 ACES, 3 Filmic, 4 Linear, 5 Psychov"));
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("shaders/display"))
                    && Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not locate repository root from " + dir);
    }
}
