package dev.comfyfluffy.caustica.rt.scene;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LodSceneContributionTest {
    @Test
    void snapshotsPublishedInstancesAndTableAddressWithoutChangingOrder() {
        RtAccel.Instance first = instance(11L, 0xC00000, 0x03);
        RtAccel.Instance second = instance(12L, 0xC00001, 0x03);
        ArrayList<RtAccel.Instance> published = new ArrayList<>(List.of(first, second));

        LodSceneContribution contribution = new LodSceneContribution(published, 37L);
        published.clear();

        assertEquals(List.of(first, second), contribution.instances());
        assertEquals(37L, contribution.tableAddress());
        assertEquals(0xC00000, contribution.instances().get(0).customIndex());
        assertEquals(0x03, contribution.instances().get(0).mask());
        assertEquals(0, contribution.instances().get(0).sbtRecordOffset());
        assertThrows(UnsupportedOperationException.class,
                () -> contribution.instances().add(first));
    }

    private static RtAccel.Instance instance(long address, int customIndex, int mask) {
        return new RtAccel.Instance(new float[] {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0
        }, address, customIndex, mask);
    }
}
