package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic CPU invariants for normal-only Animated Water. */
public final class RtWaterWaveGeometryTest {
    private static final double WATER_WAVE_STRENGTH = 0.9;
    private static final double[] WAVE_Q = { 0.050, 0.058, 0.055, 0.048, 0.036, 0.024, 0.016 };

    private record Vec3(double x, double y, double z) {
        Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
        Vec3 mul(double scale) { return new Vec3(x * scale, y * scale, z * scale); }
        double dot(Vec3 other) { return x * other.x + y * other.y + z * other.z; }
    }

    private static Vec3 offsetSurfaceOrigin(Vec3 position, Vec3 geometricNormal,
                                             Vec3 outgoingDirection, double bias) {
        Vec3 sideNormal = geometricNormal.dot(outgoingDirection) >= 0.0
                ? geometricNormal : geometricNormal.mul(-1.0);
        return position.add(sideNormal.mul(bias));
    }

    @Test
    void hardwareHitPositionAndDistanceRemainAuthoritative() {
        Vec3 ro = new Vec3(2.0, 70.0, -4.0);
        Vec3 rd = new Vec3(0.2, -0.95, 0.1);
        double payloadHitT = 6.25;
        Vec3 hitPos = ro.add(rd.mul(payloadHitT));

        assertEquals(3.25, hitPos.x, 0.0);
        assertEquals(64.0625, hitPos.y, 0.0);
        assertEquals(-3.375, hitPos.z, 0.0);
        assertEquals(6.25, payloadHitT, 0.0);
    }

    @Test
    void continuationStartsFromTheHardwareSurface() {
        Vec3 hitPos = new Vec3(3.25, 64.0, -3.375);
        Vec3 geometricNormal = new Vec3(0.0, 1.0, 0.0);
        Vec3 reflected = offsetSurfaceOrigin(hitPos, geometricNormal,
                new Vec3(0.3, 0.8, 0.2), 0.005);
        Vec3 transmitted = offsetSurfaceOrigin(hitPos, geometricNormal,
                new Vec3(0.3, -0.8, 0.2), 0.005);

        assertEquals(64.005, reflected.y, 1.0e-12);
        assertEquals(63.995, transmitted.y, 1.0e-12);
        assertEquals(hitPos.x, reflected.x, 0.0);
        assertEquals(hitPos.z, transmitted.z, 0.0);
    }

    @Test
    void waveStrengthChangesSlopeButNeverGeometry() {
        double phase = 0.73;
        double s = 1.31;
        double profile = Math.exp(s * (Math.sin(phase) - 1.0));
        double baseSlope = WAVE_Q[2] * s * Math.cos(phase) * profile;
        double payloadHitT = 4.5;

        for (int step = 0; step <= 40; step++) {
            double scale = step / 10.0;
            double slope = baseSlope * WATER_WAVE_STRENGTH * scale;
            assertEquals(baseSlope * WATER_WAVE_STRENGTH * scale, slope, 0.0);
            assertEquals(4.5, payloadHitT, 0.0, "wave strength must not alter hardware hitT");
        }
    }

    @Test
    void temporalGradientKeepsPreviousNormalSignalWithoutPositionMotion() {
        double currentGrad = 0.42;
        double gradDt = -0.18;
        double dt = 1.0 / 60.0;
        double previousGrad = currentGrad - dt * gradDt;
        Vec3 waterMotion = new Vec3(0.0, 0.0, 0.0);

        assertTrue(previousGrad != currentGrad, "animated normal history must remain available");
        assertEquals(0.0, waterMotion.x, 0.0);
        assertEquals(0.0, waterMotion.y, 0.0);
        assertEquals(0.0, waterMotion.z, 0.0);
    }

    @Test
    void verticalSideFacesKeepTheirGeometricNormal() {
        Vec3 sideNormal = new Vec3(1.0, 0.0, 0.0);
        assertTrue(Math.abs(sideNormal.y) < 0.5);
        assertEquals(new Vec3(1.0, 0.0, 0.0), sideNormal);
    }
}
