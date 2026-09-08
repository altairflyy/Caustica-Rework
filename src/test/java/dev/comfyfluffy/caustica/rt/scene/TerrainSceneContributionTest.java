package dev.comfyfluffy.caustica.rt.scene;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerrainSceneContributionTest {
    @Test
    void snapshotsPublishedInstancesWithoutChangingTheirFieldsOrOrder() {
        RtAccel.Instance first = instance(11L, 4, 0x03, 8);
        RtAccel.Instance second = instance(12L, 5, 0xff, 0);
        ArrayList<RtAccel.Instance> published = new ArrayList<>(List.of(first, second));

        TerrainSceneContribution contribution = new TerrainSceneContribution(published);
        published.clear();

        assertEquals(List.of(first, second), contribution.instances());
        assertEquals(4, contribution.instances().get(0).customIndex());
        assertEquals(0x03, contribution.instances().get(0).mask());
        assertEquals(8, contribution.instances().get(0).sbtRecordOffset());
        assertThrows(UnsupportedOperationException.class,
                () -> contribution.instances().add(first));
    }

    private static RtAccel.Instance instance(long address, int customIndex, int mask, int sbtOffset) {
        return new RtAccel.Instance(new float[] {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0
        }, address, customIndex, mask, sbtOffset);
    }
}
