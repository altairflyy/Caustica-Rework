package dev.comfyfluffy.caustica.rt.trace;

import dev.comfyfluffy.caustica.rt.RtComposite;
import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceFrameResourcesTest {
    @Test
    void rejectsInvalidRecordSizeAndStartsUnallocated() {
        assertThrows(IllegalArgumentException.class, () -> new TraceFrameResources(0));
        TraceFrameResources resources = new TraceFrameResources(1);
        assertFalse(resources.matches(configuration(1920, 1080, false, 0)));
        assertEquals(null, resources.views());
    }

    @Test
    void sizingConfigurationChangesWithExtentBackendAndQuality() {
        TraceFrameResources.Configuration base = configuration(1920, 1080, true, 2);

        assertEquals(base, configuration(1920, 1080, true, 2));
        assertNotEquals(base, configuration(2560, 1440, true, 2));
        assertNotEquals(base, configuration(1920, 1080, false, 2));
        assertNotEquals(base, configuration(1920, 1080, true, 3));
    }

    @Test
    void sizingKeyIncludesResolvedRenderExtent() {
        TraceFrameResources.Configuration configuration = configuration(1920, 1080, true, 2);
        TraceFrameResources.SizingKey first = new TraceFrameResources.SizingKey(
                configuration, new FrameContext.Extent(1280, 720));
        TraceFrameResources.SizingKey second = new TraceFrameResources.SizingKey(
                configuration, new FrameContext.Extent(960, 540));

        assertNotEquals(first, second);
    }

    @Test
    void runtimeOwnerBoundaryContainsTheCompleteWorkingSetWithoutReverseDependency() {
        Set<String> expected = Set.of("output", "continuationQueue", "gNormal", "gAlbedo", "gDepth",
                "gMotion", "gSpecAlbedo", "gSpecMotion", "gViewZ", "gNrdDiff", "gNrdSpec",
                "nrdDiffOut", "nrdSpecOut", "nrdCombined", "nrdValidation", "rrOutput", "sizingKey");
        Set<String> ownerFields = Arrays.stream(TraceFrameResources.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        Set<String> compositeFields = Arrays.stream(RtComposite.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());

        assertTrue(ownerFields.containsAll(expected));
        assertTrue(expected.stream().noneMatch(compositeFields::contains));
        assertTrue(Arrays.stream(RtComposite.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == TraceFrameResources.class));
        assertFalse(Arrays.stream(TraceFrameResources.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == RtComposite.class));
    }

    private static TraceFrameResources.Configuration configuration(int width, int height,
                                                                    boolean rr, int rrQuality) {
        return new TraceFrameResources.Configuration(new FrameContext.Extent(width, height),
                rr, rrQuality, false, Integer.MIN_VALUE, false, Integer.MIN_VALUE,
                !rr, false);
    }
}
