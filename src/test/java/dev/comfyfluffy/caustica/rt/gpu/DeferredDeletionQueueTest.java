package dev.comfyfluffy.caustica.rt.gpu;

import dev.comfyfluffy.caustica.rt.RtGpuExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DeferredDeletionQueueTest {
    @Test
    void forwardsTheExactGraphicsTokenAndDestroyCallback() throws Exception {
        AtomicReference<RtGpuExecutor.GraphicsUse> receivedUse = new AtomicReference<>();
        AtomicReference<Runnable> receivedDestroy = new AtomicReference<>();
        DeferredDeletionQueue queue = new DeferredDeletionQueue(
                (use, destroy) -> {
                    receivedUse.set(use);
                    receivedDestroy.set(destroy);
                },
                (use, destroy) -> { });
        RtGpuExecutor.GraphicsUse use = graphicsUse(41L);
        Runnable destroy = () -> { };

        queue.retireAfterGraphics(use, destroy);

        assertSame(use, receivedUse.get());
        assertSame(destroy, receivedDestroy.get());
    }

    @Test
    void forwardsTheExactTrackedTokenAndDestroyCallback() {
        AtomicReference<RtGpuExecutor.TrackedGraphicsUse> receivedUse = new AtomicReference<>();
        AtomicReference<Runnable> receivedDestroy = new AtomicReference<>();
        DeferredDeletionQueue queue = new DeferredDeletionQueue(
                (use, destroy) -> { },
                (use, destroy) -> {
                    receivedUse.set(use);
                    receivedDestroy.set(destroy);
                });
        RtGpuExecutor.TrackedGraphicsUse use = new RtGpuExecutor.TrackedGraphicsUse();
        Runnable destroy = () -> { };

        queue.retireAfterGraphics(use, destroy);

        assertSame(use, receivedUse.get());
        assertSame(destroy, receivedDestroy.get());
    }

    @Test
    void productionRuntimeRoutesPublishedRetirementThroughTheFacade() throws Exception {
        String context = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/RtContext.java"));
        assertEquals(true, context.contains("new DeferredDeletionQueue(gpuExecutor)"));

        try (var sources = Files.walk(Path.of("src/main/java"))) {
            long bypasses = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of(
                            "dev/comfyfluffy/caustica/rt/gpu/DeferredDeletionQueue.java")))
                    .filter(path -> !path.endsWith(Path.of(
                            "dev/comfyfluffy/caustica/rt/RtGpuExecutor.java")))
                    .map(DeferredDeletionQueueTest::read)
                    .flatMap(source -> source.lines())
                    .filter(line -> line.contains(".retireAfterGraphics(")
                            && !line.contains(".deferredDeletionQueue().retireAfterGraphics("))
                    .count();
            assertEquals(0L, bypasses);
        }
    }

    private static RtGpuExecutor.GraphicsUse graphicsUse(long value) throws Exception {
        Constructor<RtGpuExecutor.GraphicsUse> constructor =
                RtGpuExecutor.GraphicsUse.class.getDeclaredConstructor(long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(value);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
