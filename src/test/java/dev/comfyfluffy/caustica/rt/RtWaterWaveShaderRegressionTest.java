package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guards for animated water. The old path only tilted normals on a geometrically flat
 * sheet, which read as a repeating square texture. Animated Water must intersect a real height
 * field so crests and troughs show up in depth, reflections and refraction.
 */
final class RtWaterWaveShaderRegressionTest {
    private static final Path REPO_ROOT = repoRoot();
    private static final Path WATER = REPO_ROOT.resolve("shaders/world/water.slang");
    private static final Path PRIMARY = REPO_ROOT.resolve("shaders/world/world_primary.rgen.slang");
    private static final Path WORLD = REPO_ROOT.resolve("shaders/world/world.rgen.slang");
    private static final Path GUIDES = REPO_ROOT.resolve("shaders/world/guides.slang");

    @Test
    void spectrumAccumulatesHeightNotJustGradient() throws IOException {
        String water = Files.readString(WATER);

        assertFalse(water.contains("gradient-only"),
                "the surface must no longer stay geometrically flat");
        assertFalse(water.contains("the surface stays geometrically"),
                "animated water is a height field, not a normal-only fake");
        assertTrue(water.contains("height += a * e;"),
                "the spectrum must accumulate the sharp-crested height used for displacement");
        assertTrue(water.contains("public float waterWaveHeight("),
                "height must be a first-class spectrum output, not reconstructed from the gradient");
    }

    @Test
    void animatedWaterIntersectsTheHeightFieldAlongTheViewRay() throws IOException {
        String water = Files.readString(WATER);
        String intersect = slice(water, "public WaterWaves evaluateWaterWaves(", "\n}\n");

        assertTrue(intersect.contains("WAVE_NEWTON_STEPS"),
                "the rest-plane hit must be refined onto the displaced surface");
        assertTrue(intersect.contains("float f = pos.y - restY - height;"),
                "Newton residual is ray Y minus rest plane minus wave height");
        assertTrue(intersect.contains("waves.position = pos;"),
                "the shading point must ride the height field");
        assertTrue(intersect.contains("waves.motionPrev = float3(0.0, height - previousHeight, 0.0);"),
                "vertical wave motion has to reach DLSS/NRD or the surface will ghost");
    }

    @Test
    void radianceAndGuidePassesUseGeometricWavesWhenTheToggleIsOn() throws IOException {
        String primary = Files.readString(PRIMARY);
        String world = Files.readString(WORLD);
        String guides = Files.readString(GUIDES);

        assertTrue(primary.contains("evaluateWaterWaves(") && primary.contains("waterWaveContinueOrigin("),
                "Pass A must displace the primary water hit and continue from the height field");
        assertTrue(world.contains("evaluateWaterWaves(") && world.contains("waterWaveContinueOrigin("),
                "Pass B must displace water hits the same way as Pass A");
        assertTrue(guides.contains("evaluateWaterWaves(") && guides.contains("waterWaveContinueOrigin("),
                "transmission guides must follow the displaced surface or RR will halo");
        assertTrue(primary.contains("isWater ? waterSurf.motionPrev : payload.motionPrev"),
                "primary water motion must come from the height-field time derivative");
    }

    @Test
    void continuationOriginsStayOffTheRestPlaneMesh() throws IOException {
        String origin = slice(Files.readString(WATER), "public float3 waterWaveContinueOrigin(", "\n}\n");

        assertTrue(origin.contains("goingOut > 0.0"),
                "reflections must stay outside the rest-plane triangle");
        assertTrue(origin.contains("origin -= waves.geometricNormal * (planeSide + bias)"),
                "transmissions must start inside the volume even from a crest above the mesh");
    }

    @Test
    void waveHeightSearchBoundScalesWithStrength() throws IOException {
        String water = Files.readString(WATER);
        String eval = slice(water, "public WaterWaves evaluateWaterWaves(", "\n}\n");

        assertTrue(eval.contains("waterWaveStrengthScale()") || eval.contains("waterWaveMaxHeight()"),
                "Newton search bound must scale with wave strength to prevent root clipping at wave height > 1");
    }

    @Test
    void allPrimaryContinuationBranchesUseWaveAwareOrigin() throws IOException {
        String primary = Files.readString(PRIMARY);
        String cont = slice(primary, "if (!hasTransmission || F >= 1.0) {", "return continuation;\n    }");

        assertTrue(cont.contains("waterWaveContinueOrigin") || cont.contains("waterContinueOrigin"),
                "TIR and non-split reflection must use wave-aware continuation origin");
    }

    @Test
    void specularGuideProbeUsesWaveAwareContinuationOrigin() throws IOException {
        String guides = Files.readString(GUIDES);
        String spec = slice(guides, "public float2 resolveSpecularGuides(", "if (payload.hitT > 0.0) {");

        assertTrue(spec.contains("waveContinueOrigin") || spec.contains("waterWaveContinueOrigin") || spec.contains("waterContinueOrigin"),
                "Specular guide probe must use wave-aware continuation origin on animated water");
    }

    @Test
    void physicalTravelledQuantitiesUseEffectiveHitT() throws IOException {
        String primary = Files.readString(PRIMARY);
        String world = Files.readString(WORLD);

        assertTrue(primary.contains("effectiveHitT"),
                "Primary raygen must define and use effectiveHitT for physical path distances");
        assertTrue(world.contains("fogSegment(worldPush, ro, rd, effectiveHitT, seed, showCelestial);"),
                "World raygen must integrate atmospheric fog up to effectiveHitT so water displacement bounds media");
        assertTrue(world.contains("effectiveHitT, cloudSkyAmbient(worldPush),"),
                "World raygen must bound clouds by effectiveHitT so water displacement bounds media");
    }

    private static String slice(String source, String startNeedle, String endNeedle) {
        source = source.replace("\r\n", "\n").replace('\r', '\n');
        int start = source.indexOf(startNeedle);
        assertTrue(start >= 0, "missing shader snippet start: " + startNeedle);
        int end = source.indexOf(endNeedle, start);
        assertTrue(end > start, "missing shader snippet end: " + endNeedle);
        return source.substring(start, end);
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
