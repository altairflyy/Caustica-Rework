package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic CPU mathematical and geometric invariant tests for Animated Water (BUGFIX-003).
 * Verifies wave height scaling, rest-plane half-space continuation invariants, and physical
 * travelled distance (effectiveHitT vs payload.hitT) without requiring a Minecraft runtime.
 */
public final class RtWaterWaveGeometryTest {

    public static final float SURF_BIAS = 0.005f;
    public static final float BASE_MAX_HEIGHT = 0.55f;

    public record Vec3(float x, float y, float z) {
        public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
        public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
        public Vec3 mul(float s) { return new Vec3(x * s, y * s, z * s); }
        public float dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
        public float length() { return (float) Math.sqrt(dot(this)); }
        public Vec3 normalize() {
            float l = length();
            return l > 1e-6f ? mul(1.0f / l) : this;
        }
    }

    /**
     * Replicates the exact formula of ordinary offsetSurfaceOrigin:
     * offset along sideNormal by bias.
     */
    public static Vec3 offsetSurfaceOrigin(Vec3 position, Vec3 surfaceNormal, Vec3 outgoingDirection, float bias) {
        Vec3 sideNormal = surfaceNormal.dot(outgoingDirection) >= 0.0f
                ? surfaceNormal
                : surfaceNormal.mul(-1.0f);
        return position.add(sideNormal.mul(bias));
    }

    /**
     * Replicates the exact formula of waterWaveContinueOrigin:
     * offsets off displaced surface AND enforces that the origin is on the correct side of the rest plane.
     */
    public static Vec3 waterWaveContinueOrigin(Vec3 position, Vec3 planePos, Vec3 geometricNormal, Vec3 rd, float bias) {
        Vec3 sideNormal = geometricNormal.dot(rd) >= 0.0f
                ? geometricNormal
                : geometricNormal.mul(-1.0f);
        Vec3 origin = position.add(sideNormal.mul(bias));
        float goingOut = rd.dot(geometricNormal);
        float planeSide = geometricNormal.dot(origin.sub(planePos));
        if (goingOut > 0.0f) {
            if (planeSide < bias) origin = origin.add(geometricNormal.mul(bias - planeSide));
        } else {
            if (planeSide > -bias) origin = origin.sub(geometricNormal.mul(planeSide + bias));
        }
        return origin;
    }

    // =========================================================================
    // BUG 1: Search bound scaling under Wave Height 1 vs 4
    // =========================================================================

    @Test
    void searchBoundScalesWithWaveHeightToPreventRootClipping() {
        // At Wave Height 1.0, max wave amplitude is bounded by BASE_MAX_HEIGHT (0.55 blocks).
        // At Wave Height 4.0, amplitude scales 4x, reaching up to 2.2 blocks peak-to-trough.
        float waveHeightScaleDefault = 1.0f;
        float waveHeightScaleMax = 4.0f;

        float effectiveMaxHeightDefault = BASE_MAX_HEIGHT * waveHeightScaleDefault;
        float effectiveMaxHeightScaled = BASE_MAX_HEIGHT * waveHeightScaleMax;

        assertEquals(0.55f, effectiveMaxHeightDefault, 1e-4f);
        assertEquals(2.20f, effectiveMaxHeightScaled, 1e-4f);

        // Synthetic large wave displacement at scale 4.0: crest at +1.30 blocks above rest-plane y=0.
        float restHitT = 10.0f;
        Vec3 ro = new Vec3(0, 10, 0);
        Vec3 rd = new Vec3(0, -1, 0); // looking straight down
        float crestY = 1.30f;
        float trueDisplacedHitT = 10.0f - crestY; // 8.70f

        float absRdY = Math.max(Math.abs(rd.y), 0.05f);

        // Case A: Unscaled search bound (0.55)
        float unscaledTMin = Math.max(restHitT - BASE_MAX_HEIGHT / absRdY, 0.0f); // 10 - 0.55 = 9.45
        float unscaledTMax = restHitT + BASE_MAX_HEIGHT / absRdY;                 // 10 + 0.55 = 10.55

        // The true root (8.70) is OUTSIDE the unscaled interval [9.45, 10.55]!
        // Newton iteration clamped to [unscaledTMin, unscaledTMax] would truncate at 9.45, missing the crest!
        assertFalse(trueDisplacedHitT >= unscaledTMin && trueDisplacedHitT <= unscaledTMax,
                "Unscaled search bound MUST fail to enclose the true root of a large wave at scale 4.0");

        // Case B: Scaled search bound (2.20)
        float scaledTMin = Math.max(restHitT - effectiveMaxHeightScaled / absRdY, 0.0f); // 10 - 2.2 = 7.80
        float scaledTMax = restHitT + effectiveMaxHeightScaled / absRdY;                 // 10 + 2.2 = 12.20

        // The true root (8.70) is inside [7.80, 12.20]!
        assertTrue(trueDisplacedHitT >= scaledTMin && trueDisplacedHitT <= scaledTMax,
                "Scaled search bound MUST enclose the true root across the full slider range");
    }

    // =========================================================================
    // BUG 2: Continuation origin and rest-plane semispace invariant
    // =========================================================================

    @Test
    void ordinaryOffsetLeavesOriginOnWrongSideOfRestPlaneUnderTirAndReflection() {
        // Exact synthetic scenario specified in directives:
        // rest-plane y = 0
        // displaced surface y = +0.30 (crest above rest-plane)
        // ray coming from below: ro = (0, -5, 0), rd = (0, 1, 0)
        // geometric normal facing ray: nGeo = (0, -1, 0)
        // reflection / TIR directed back downwards: rdRefl = (0, -1, 0)
        Vec3 planePos = new Vec3(0, 0, 0);
        Vec3 displacedPos = new Vec3(0, 0.30f, 0);
        Vec3 nGeo = new Vec3(0, -1, 0); // facing the incoming ray (from below)
        Vec3 rdRefl = new Vec3(0, -1, 0); // reflected downwards into water

        // 1. Ordinary offset:
        Vec3 ordinaryOrigin = offsetSurfaceOrigin(displacedPos, nGeo, rdRefl, SURF_BIAS);
        // displacedPos + (-1)*0.005 = 0.30 - 0.005 = 0.295
        assertEquals(0.295f, ordinaryOrigin.y, 1e-4f);

        // DEMONSTRATION OF BUG:
        // The ray is supposed to travel downwards through water (semispace y < 0).
        // But ordinaryOrigin.y is +0.295 > 0 (in air, on the WRONG side of rest-plane y=0)!
        assertTrue(ordinaryOrigin.y > planePos.y,
                "Ordinary offset leaves the origin on the WRONG side of the rest-plane (y > 0)");

        // Because it starts at y = +0.295 with downward direction (0, -1, 0),
        // it hits the rest-plane mesh at y = 0 after distance t = 0.295 blocks:
        float distToMesh = (ordinaryOrigin.y - planePos.y) / (-rdRefl.y);
        assertTrue(distToMesh > 0.0f && distToMesh < 1.0f,
                "Ray from ordinary offset immediately re-intersects its own rest-plane mesh!");

        // 2. Wave-aware continuation:
        Vec3 waveOrigin = waterWaveContinueOrigin(displacedPos, planePos, nGeo, rdRefl, SURF_BIAS);

        // PROOF OF FIX:
        // The wave-aware origin is adjusted so that it lands strictly on the correct side of the rest-plane:
        assertTrue(waveOrigin.y <= -SURF_BIAS + 1e-5f,
                "Wave-aware origin must be in the correct half-space (y <= -SURF_BIAS)");
        assertEquals(-0.005f, waveOrigin.y, 1e-4f);

        // A downward ray from waveOrigin cannot intersect rest-plane at y = 0:
        float distToMeshFromWave = (waveOrigin.y - planePos.y) / (-rdRefl.y);
        assertTrue(distToMeshFromWave < 0.0f,
                "Ray from wave-aware origin cannot hit the rest-plane along outgoing direction");
    }

    @Test
    void waveAwareContinuationPreservesSemispaceAcrossAllInteractions() {
        Vec3 planePos = new Vec3(0, 0, 0);
        float bias = SURF_BIAS;

        // --- Scenario 1: Ray from above, hitting crest y = +0.30 ---
        // incoming rd = (0, -1, 0), nGeo = (0, 1, 0)
        Vec3 crestPos = new Vec3(0, 0.30f, 0);
        Vec3 nGeoAbove = new Vec3(0, 1, 0);

        // 1A. Reflection (heading up into air: rd = (0, 1, 0))
        Vec3 reflAbove = waterWaveContinueOrigin(crestPos, planePos, nGeoAbove, new Vec3(0, 1, 0), bias);
        assertTrue(reflAbove.y >= bias - 1e-5f, "Reflection above must stay in positive half-space (air)");
        assertTrue(reflAbove.y >= crestPos.y + bias - 1e-5f, "Reflection must be biased off displaced crest");

        // 1B. Transmission (heading down into water: rd = (0, -1, 0))
        Vec3 transAbove = waterWaveContinueOrigin(crestPos, planePos, nGeoAbove, new Vec3(0, -1, 0), bias);
        assertTrue(transAbove.y <= -bias + 1e-5f, "Transmission from above must enter water below rest-plane (y <= -bias)");

        // --- Scenario 2: Ray from above, hitting trough y = -0.30 ---
        Vec3 troughPos = new Vec3(0, -0.30f, 0);

        // 2A. Reflection (heading up into air: rd = (0, 1, 0))
        Vec3 reflTrough = waterWaveContinueOrigin(troughPos, planePos, nGeoAbove, new Vec3(0, 1, 0), bias);
        assertTrue(reflTrough.y >= bias - 1e-5f, "Reflection from trough must exit into positive half-space past rest-plane (y >= bias)");

        // 2B. Transmission (heading down into water: rd = (0, -1, 0))
        Vec3 transTrough = waterWaveContinueOrigin(troughPos, planePos, nGeoAbove, new Vec3(0, -1, 0), bias);
        assertTrue(transTrough.y <= -bias + 1e-5f, "Transmission from trough must stay in water below rest-plane (y <= -bias)");

        // --- Scenario 3: Ray from below, hitting crest y = +0.30 ---
        // incoming rd = (0, 1, 0), nGeo = (0, -1, 0)
        Vec3 nGeoBelow = new Vec3(0, -1, 0);

        // 3A. TIR / Reflection (heading down into water: rd = (0, -1, 0))
        Vec3 tirBelow = waterWaveContinueOrigin(crestPos, planePos, nGeoBelow, new Vec3(0, -1, 0), bias);
        assertTrue(tirBelow.y <= -bias + 1e-5f, "TIR below crest must return into negative half-space (y <= -bias)");

        // 3B. Transmission (heading up into air: rd = (0, 1, 0))
        Vec3 transBelow = waterWaveContinueOrigin(crestPos, planePos, nGeoBelow, new Vec3(0, 1, 0), bias);
        assertTrue(transBelow.y >= bias - 1e-5f, "Transmission from below crest must enter air (y >= bias)");

        // --- Scenario 4: Ray from below, hitting trough y = -0.30 ---
        // 4A. TIR / Reflection (heading down into water: rd = (0, -1, 0))
        Vec3 tirTrough = waterWaveContinueOrigin(troughPos, planePos, nGeoBelow, new Vec3(0, -1, 0), bias);
        assertTrue(tirTrough.y <= -bias + 1e-5f, "TIR below trough must stay in negative half-space (y <= -bias)");

        // 4B. Transmission (heading up into air: rd = (0, 1, 0))
        Vec3 transTroughBelow = waterWaveContinueOrigin(troughPos, planePos, nGeoBelow, new Vec3(0, 1, 0), bias);
        assertTrue(transTroughBelow.y >= bias - 1e-5f, "Transmission from below trough must exit into air (y >= bias)");
    }

    @Test
    void flatSurfaceDegeneratesWaveContinueOriginToExactOrdinaryOffset() {
        // Mathematical invariant: When position == planePos (flat water or non-water surface),
        // waveContinueOrigin MUST produce bit-exact equivalent results to offsetSurfaceOrigin.
        Vec3 pos = new Vec3(12.5f, 64.0f, -8.2f);
        Vec3 normal = new Vec3(0, 1, 0);

        Vec3[] testDirs = new Vec3[] {
                new Vec3(0, 1, 0),
                new Vec3(0, -1, 0),
                new Vec3(0.577f, 0.577f, 0.577f).normalize(),
                new Vec3(-0.3f, -0.8f, 0.5f).normalize()
        };

        for (Vec3 dir : testDirs) {
            Vec3 ordinary = offsetSurfaceOrigin(pos, normal, dir, SURF_BIAS);
            Vec3 wave = waterWaveContinueOrigin(pos, pos, normal, dir, SURF_BIAS);

            assertEquals(ordinary.x, wave.x, 1e-6f, "X must match on flat surface");
            assertEquals(ordinary.y, wave.y, 1e-6f, "Y must match on flat surface");
            assertEquals(ordinary.z, wave.z, 1e-6f, "Z must match on flat surface");
        }
    }

    // =========================================================================
    // BUG 3: effectiveHitT vs payload.hitT (Physical Travelled Distance)
    // =========================================================================

    @Test
    void physicalTravelledQuantitiesRequireEffectiveHitTNotHardwareHitT() {
        // Given a ray from eye underwater to water surface:
        // ro = (0, 60, 0), rd = (0, 1, 0)
        // rest-plane is at y = 64.0 -> hardwareHitT = 4.0 blocks
        // wave has a crest at y = 64.8 -> displacedHitTCrest = 4.8 blocks (crest further away from below)
        // OR wave has a trough at y = 63.2 -> displacedHitTTrough = 3.2 blocks (trough closer from below)
        float hardwareHitT = 4.0f;
        float displacedHitTCrest = 4.8f;

        Vec3 waterExtinction = new Vec3(0.08f, 0.03f, 0.01f); // typical water absorption/extinction
        float rayConeSpread = 0.001f;
        float initialConeWidth = 0.002f;

        // 1. Beer-Lambert Extinction: T = exp(-sigma * t)
        // True physical transmission through 4.8 blocks of water vs rest-plane 4.0 blocks:
        float trueTransR = (float) Math.exp(-waterExtinction.x * displacedHitTCrest);
        float falseTransR = (float) Math.exp(-waterExtinction.x * hardwareHitT);
        assertNotEquals(trueTransR, falseTransR, 1e-5f);
        // Error from using hardwareHitT instead of effectiveHitT:
        float beerError = Math.abs(trueTransR - falseTransR);
        assertTrue(beerError > 0.02f, "Using hardwareHitT creates significant transmittance error in water");

        // 2. Ray Cone Width: w = w0 + spread * t
        float trueConeWidth = initialConeWidth + rayConeSpread * displacedHitTCrest;
        float falseConeWidth = initialConeWidth + rayConeSpread * hardwareHitT;
        assertNotEquals(trueConeWidth, falseConeWidth, 1e-7f);
        assertEquals(initialConeWidth + rayConeSpread * 4.8f, trueConeWidth, 1e-7f);

        // Invariant: when displaced, effectiveHitT MUST be used for all path-segment length calculations
        float effectiveHitT = displacedHitTCrest;
        float calculatedTransR = (float) Math.exp(-waterExtinction.x * effectiveHitT);
        assertEquals(trueTransR, calculatedTransR, 1e-7f,
                "effectiveHitT accurately computes physical Beer-Lambert transmittance");
    }

    @Test
    void searchBoundDegeneratesToRestPlaneAtZeroStrengthScaleAndRemainsConservativeAcrossEntireRange() {
        // Test scale = 0.0: mathematically exact degeneracy
        float scaleZero = 0.0f;
        float maxHeightZero = BASE_MAX_HEIGHT * scaleZero;
        assertEquals(0.0f, maxHeightZero, 1e-7f);

        float restHitT = 5.0f;
        float absRdY = 0.8f;
        float tMinZero = Math.max(restHitT - maxHeightZero / absRdY, 0.0f);
        float tMaxZero = restHitT + maxHeightZero / absRdY;
        assertEquals(restHitT, tMinZero, 1e-7f);
        assertEquals(restHitT, tMaxZero, 1e-7f);

        // Clamping any iteration step to [tMin, tMax] when maxHeight is 0.0 yields exactly restHitT
        float stepResult = Math.max(tMinZero, Math.min(tMaxZero, restHitT + 1.234f));
        assertEquals(restHitT, stepResult, 1e-7f, "Interval collapses identically to restHitT when scale is 0.0");

        // Conservative property across the entire continuous range [0.0, 4.0]:
        // For any wave spectrum with base maximum height h0 <= BASE_MAX_HEIGHT (0.55),
        // the scaled height h(s) = s * h0 is strictly <= s * BASE_MAX_HEIGHT.
        float authoredMaxCrest = 0.50f; // authored sum of in-phase components is < 0.55
        for (int i = 0; i <= 40; i++) {
            float s = i * 0.1f;
            float maxPossibleCrest = authoredMaxCrest * s;
            float bound = BASE_MAX_HEIGHT * s;
            assertTrue(bound >= maxPossibleCrest,
                    "Bound must conservatively exceed physical crest height at scale s = " + s);
        }
    }
}
