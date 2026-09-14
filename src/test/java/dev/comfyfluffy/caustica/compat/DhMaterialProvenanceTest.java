package dev.comfyfluffy.caustica.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DhMaterialProvenanceTest {
    @Test
    void gainUsesOnlyMeasuredLuminanceRatio() {
        assertEquals(1.25f, DhMaterialProvenance.gainForTest(0.5f, 0.4f), 1.0e-6f);
        assertEquals(0.5f, DhMaterialProvenance.gainForTest(0.2f, 0.4f), 1.0e-6f);
    }

    @Test
    void invalidMeasurementsUseNeutralFallback() {
        assertEquals(1.0f, DhMaterialProvenance.gainForTest(0.5f, 0.0f));
        assertEquals(1.0f, DhMaterialProvenance.gainForTest(Float.NaN, 0.4f));
        assertEquals(1.0f, DhMaterialProvenance.gainForTest(Float.POSITIVE_INFINITY, 0.4f));
    }

    @Test
    void finalQuadResolvesOnlyOneAuthoritativeSurfaceIdentity() {
        int cells = 64 * 64;
        int[] ids = new int[cells];
        short[] yMin = new short[cells];
        short[] yMax = new short[cells];
        ids[0] = 7;
        yMax[0] = 1;

        assertEquals(7, DhMaterialProvenance.resolveQuadForTest(
                1, 1, ids, yMin, yMax, topQuad(0, 0, 1)));

        ids[1] = 7;
        ids[64] = 8; // x=1,z=0 in the x-major ColumnRenderSource layout
        ids[65] = 8;
        yMax[1] = yMax[64] = yMax[65] = 1;
        assertEquals(DhMaterialProvenance.UNKNOWN, DhMaterialProvenance.resolveQuadForTest(
                1, 1, ids, yMin, yMax, topQuad(0, 0, 2)));
    }

    private static byte[] topQuad(int x, int z, int width) {
        byte[] bytes = new byte[64];
        putPosition(bytes, 0, x, 1, z);
        putPosition(bytes, 16, x + width, 1, z);
        putPosition(bytes, 32, x + width, 1, z + width);
        putPosition(bytes, 48, x, 1, z + width);
        bytes[13] = 1;
        return bytes;
    }

    private static void putPosition(byte[] bytes, int offset, int x, int y, int z) {
        putU16(bytes, offset, x);
        putU16(bytes, offset + 2, y);
        putU16(bytes, offset + 4, z);
    }

    private static void putU16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }
}
