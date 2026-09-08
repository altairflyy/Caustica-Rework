package dev.comfyfluffy.caustica.rt.entity;

import dev.comfyfluffy.caustica.rt.accel.RtAccel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntitySceneContributionTest {
    @Test
    void snapshotsInstanceAndBuildSegmentsWithoutChangingOrder() {
        RtAccel.Instance base = instance(11L, 2, 0xff, 0);
        RtAccel.Instance dynamic = instance(12L, 7, 0x03, 8);
        ArrayList<RtAccel.Instance> baseInstances = new ArrayList<>(List.of(base));
        ArrayList<RtAccel.Instance> dynamicInstances = new ArrayList<>(List.of(dynamic));

        RtEntities.EntitySceneContribution contribution = new RtEntities.EntitySceneContribution(
                baseInstances, dynamicInstances, List.of(), 37L);
        baseInstances.clear();
        dynamicInstances.clear();

        assertEquals(List.of(base), contribution.baseInstances());
        assertEquals(List.of(dynamic), contribution.dynamicInstances());
        assertEquals(37L, contribution.geomTableAddr());
        assertEquals(7, contribution.dynamicInstances().get(0).customIndex());
        assertEquals(0x03, contribution.dynamicInstances().get(0).mask());
        assertEquals(8, contribution.dynamicInstances().get(0).sbtRecordOffset());
        assertThrows(UnsupportedOperationException.class,
                () -> contribution.dynamicInstances().add(dynamic));
    }

    private static RtAccel.Instance instance(long address, int customIndex, int mask, int sbtOffset) {
        return new RtAccel.Instance(new float[] {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0
        }, address, customIndex, mask, sbtOffset);
    }
}
