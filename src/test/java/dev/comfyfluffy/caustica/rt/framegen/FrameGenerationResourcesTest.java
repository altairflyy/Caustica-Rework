package dev.comfyfluffy.caustica.rt.framegen;

import dev.comfyfluffy.caustica.rt.RtComposite;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.gpu.FrameTailRetirement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameGenerationResourcesTest {
    @Test
    void freshOwnerStartsResetAndWithoutHistory() {
        FrameGenerationResources resources = new FrameGenerationResources(destruction -> { });

        assertTrue(resources.resetPending());
        assertFalse(resources.historyValid());
    }

    @Test
    void invalidationKeepsResetPendingAndDropsHistory() {
        FrameGenerationResources resources = new FrameGenerationResources(destruction -> { });

        resources.invalidate();

        assertTrue(resources.resetPending());
        assertFalse(resources.historyValid());
    }

    @Test
    void runtimeOwnerBoundaryIsUniqueAndDoesNotDependOnComposite() {
        Set<String> migratedFields = Set.of(
                "hudlessImage", "hdrHudlessImage", "interp", "backbufferCopy", "previousFrame",
                "nativePipeline", "uiCompositePipeline", "skyMaskPipeline", "reset",
                "previousFrameValid", "nativeLastUseFrame", "nativeSeededTick", "nativeCameraValid",
                "jitterX", "jitterY", "clipToPrevious", "previousToClip");
        Set<String> compositeFields = Arrays.stream(RtComposite.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(Arrays.stream(RtComposite.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == FrameGenerationResources.class));
        assertTrue(Arrays.stream(FrameGenerationResources.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == FrameTailRetirement.class));
        assertTrue(Arrays.stream(FrameGenerationResources.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == RtImage.class));
        assertTrue(migratedFields.stream().noneMatch(compositeFields::contains));
        assertTrue(Arrays.stream(FrameGenerationResources.class.getDeclaredFields())
                .noneMatch(field -> field.getType() == RtComposite.class));
    }
}
