package dev.comfyfluffy.caustica.rt.trace;

import dev.comfyfluffy.caustica.rt.RtComposite;
import dev.comfyfluffy.caustica.rt.pipeline.RtPipeline;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTraceResourcesTest {
    @Test
    void rejectsInvalidImmutableConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new WorldTraceResources(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new WorldTraceResources(1, 0));
    }

    @Test
    void frameViewsDefensivelyCopyBorrowedHandles() {
        long[] guides = {2L, 3L, 4L};
        WorldTraceResources.FrameViews views = new WorldTraceResources.FrameViews(1L, guides);

        guides[0] = 99L;
        long[] returned = views.guideViews();
        returned[1] = 98L;

        assertEquals(1L, views.outputView());
        assertArrayEquals(new long[]{2L, 3L, 4L}, views.guideViews());
    }

    @Test
    void pushSlotRotationWrapsAcrossTheWholeRing() {
        int slot = 0;
        for (int expected = 1; expected < 6; expected++) {
            slot = WorldTraceResources.nextPushSlot(slot);
            assertEquals(expected, slot);
        }
        assertEquals(0, WorldTraceResources.nextPushSlot(slot));
    }

    @Test
    void freshOwnerRequiresFallbackAndHasNoMaterialGateToConsume() {
        WorldTraceResources resources = new WorldTraceResources(1, 1);

        assertTrue(resources.requiresVanillaFallback());
        assertFalse(resources.consumeMaterialEpochTraceGate());
    }

    @Test
    void runtimeOwnerBoundaryIsExplicitAndNotDuplicatedInComposite() {
        Set<String> migratedFields = Set.of(
                "worldPipeline", "atlasSampler", "pushRing", "pushSlot",
                "reloadRebindRequested", "boundBlockAlbedoAtlasHandle",
                "bindlessTextureCapacity", "materialBindingsReady", "materialEpochTraceGate");
        Set<String> compositeFields = Arrays.stream(RtComposite.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(Arrays.stream(RtComposite.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == WorldTraceResources.class));
        assertTrue(Arrays.stream(WorldTraceResources.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == RtPipeline.class));
        assertTrue(migratedFields.stream().noneMatch(compositeFields::contains));
        assertTrue(Arrays.stream(WorldTraceResources.class.getDeclaredFields())
                .noneMatch(field -> field.getType() == RtComposite.class));
    }
}
