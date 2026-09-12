package dev.comfyfluffy.caustica.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DhSurfaceDataPackingTest {
    @Test
    void everySupportedSurfaceKeySurvivesRgba8UnormRoundTrip() {
        for (int face = 0; face < 6; face++) {
            for (boolean water : new boolean[] {false, true}) {
                for (int sky = 0; sky < 16; sky++) {
                    for (int block = 0; block < 16; block++) {
                        int packed = pack(face, water, sky, block);
                        float encodedAlpha = packed / 255.0f;
                        int sampled = Math.round(encodedAlpha * 255.0f);

                        assertEquals(face, sampled & 7);
                        assertEquals(water, (sampled & 8) != 0);
                        assertEquals(quantizeLight(sky), (sampled >>> 4) & 3);
                        assertEquals(quantizeLight(block), (sampled >>> 6) & 3);
                    }
                }
            }
        }
    }

    @Test
    void lightQuantizationCoversTheFullFourBitRangeMonotonically() {
        int previous = -1;
        for (int level = 0; level < 16; level++) {
            int quantized = quantizeLight(level);
            assertTrue(quantized >= previous);
            previous = quantized;
        }
        assertEquals(0, quantizeLight(0));
        assertEquals(3, quantizeLight(15));
    }

    private static int pack(int face, boolean water, int sky, int block) {
        return (face & 7)
                | (water ? 8 : 0)
                | ((quantizeLight(sky) & 3) << 4)
                | ((quantizeLight(block) & 3) << 6);
    }

    private static int quantizeLight(int level) {
        return (level + 2) / 5;
    }
}
