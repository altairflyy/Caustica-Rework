package dev.comfyfluffy.caustica.rt.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeatherSceneContributionTest {
    @Test
    void identifiesTheExactSharedParticleVertexSegment() {
        WeatherSceneContribution contribution = new WeatherSceneContribution(3, 12, 24);

        assertEquals(3, contribution.columnCount());
        assertEquals(12, contribution.firstVertex());
        assertEquals(24, contribution.vertexCount());
        assertEquals(36, contribution.endVertex());
    }

    @Test
    void emptyContributionKeepsTheCurrentAppendPosition() {
        assertEquals(new WeatherSceneContribution(0, 9, 0),
                WeatherSceneContribution.empty(9));
    }

    @Test
    void rejectsInvalidSceneRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherSceneContribution(-1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherSceneContribution(0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WeatherSceneContribution(0, 0, -1));
    }
}
