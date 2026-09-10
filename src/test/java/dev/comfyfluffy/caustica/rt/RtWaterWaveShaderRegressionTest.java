package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression guards for the normal-only Animated Water geometry contract. */
final class RtWaterWaveShaderRegressionTest {
    private static final Path REPO_ROOT = repoRoot();
    private static final Path WATER = REPO_ROOT.resolve("shaders/world/water.slang");
    private static final Path PRIMARY = REPO_ROOT.resolve("shaders/world/world_primary.rgen.slang");
    private static final Path WORLD = REPO_ROOT.resolve("shaders/world/world.rgen.slang");
    private static final Path GUIDES = REPO_ROOT.resolve("shaders/world/guides.slang");

    @Test
    void waveModuleContainsNoSecondGeometricSurface() throws IOException {
        String water = Files.readString(WATER);
        for (String banned : new String[] {
                "public struct WaterWaves", "evaluateWaterWaves", "waterWaveContinueOrigin",
                "waterContinueOrigin", "waterWaveHeight", "waterWaveMaxHeight",
                "WAVE_MAX_HEIGHT", "WAVE_NEWTON_STEPS", "effectiveHitT"
        }) {
            assertFalse(water.contains(banned), "normal-only water must not retain " + banned);
        }
        assertTrue(water.contains("public float3 applyWaterWaves("));
        assertTrue(water.contains("if (abs(nGeo.y) < 0.5) return nGeo;"),
                "near-vertical fluid faces must retain the geometric normal");
    }

    @Test
    void allRayPathsKeepHardwareGeometryAndPerturbOnlyNormals() throws IOException {
        String primary = Files.readString(PRIMARY);
        String world = Files.readString(WORLD);
        String guides = Files.readString(GUIDES);
        for (String source : new String[] { primary, world, guides }) {
            assertFalse(source.contains("evaluateWaterWaves("));
            assertFalse(source.contains("effectiveHitT"));
            assertFalse(source.contains("waterSurf.position"));
            assertFalse(source.contains("waterWaveContinueOrigin"));
            assertTrue(source.contains("payload.hitT"), "hardware traversal distance must remain authoritative");
            assertTrue(source.contains("offsetSurfaceOrigin("), "continuations must start at the BLAS hit");
        }
        assertTrue(world.contains("n = applyWaterWaves(geometricNormal,"));
        assertTrue(guides.contains("interfaceNormal = applyWaterWaves(geometricNormal,"));
        assertTrue(primary.contains("waterWaveGradTemporal("),
                "primary must retain current/previous animated shading normals");
    }

    @Test
    void physicalSegmentQuantitiesUseHardwareHitDistance() throws IOException {
        String primary = Files.readString(PRIMARY);
        String world = Files.readString(WORLD);
        String guides = Files.readString(GUIDES);

        assertTrue(primary.contains("medium.current.extinction * payload.hitT"));
        assertTrue(primary.contains("rayConeSpread * max(payload.hitT, 0.0)"));
        assertTrue(world.contains("fogSegment(worldPush, ro, rd, payload.hitT, seed, showCelestial);"));
        assertTrue(world.contains("payload.hitT, cloudSkyAmbient(worldPush),"));
        assertTrue(guides.contains("medium.current.extinction * payload.hitT"));
    }

    @Test
    void waterHasStablePositionMotionButAnimatedPreviousNormal() throws IOException {
        String primary = Files.readString(PRIMARY);
        assertTrue(primary.contains("previousNormal = orientation * normalize("));
        assertTrue(primary.contains("gv_motionObjDisp = isWater ? float3(0.0, 0.0, 0.0)"));
        assertTrue(primary.contains("isWater ? float3(0.0, 0.0, 0.0) : float3(payload.motionPrev)"));
        assertFalse(primary.contains("height - previousHeight"));
    }

    @Test
    void advancedSpectrumAndCausticsRemainActive() throws IOException {
        String water = Files.readString(WATER);
        for (String required : new String[] {
                "WAVE_LAMBDA_RATIO", "WAVE_MEANDER", "waterWaveLodWeight",
                "waterWaveSpeedScale()", "waterWaveCount()", "dphDt", "waterWaveGradTemporal",
                "public float waterCaustic(", "float2 g = waterWaveGrad(xz, t);"
        }) {
            assertTrue(water.contains(required), "advanced wave feature missing: " + required);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("shaders/world"))
                    && Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not locate the repository root from " + dir);
    }
}
