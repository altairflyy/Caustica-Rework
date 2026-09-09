package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioral shutdown-state tests plus structural ordering characterization at the Vulkan boundary. */
final class GpuShutdownQuiescenceTest {
    @Test
    void shutdownStateRejectsSubmissionsAndRequiresQuiescenceBeforeFinalDestroy() {
        GpuShutdownState state = new GpuShutdownState();
        state.requireSubmissionAllowed();
        assertTrue(state.beginQuiescence());
        assertFalse(state.beginQuiescence());
        assertThrows(IllegalStateException.class, state::requireSubmissionAllowed);
        assertThrows(IllegalStateException.class, state::shouldDestroyInfrastructure);

        state.markQuiesced();
        assertTrue(state.isQuiesced());
        assertTrue(state.shouldDestroyInfrastructure());
        state.markDestroyed();
        assertFalse(state.shouldDestroyInfrastructure());
    }

    @Test
    void clientEstablishesQuiescenceBeforeGpuOwnerTeardown() throws Exception {
        String source = read("src/main/java/dev/comfyfluffy/caustica/client/CausticaClient.java");
        int terrain = source.indexOf("RtTerrain.shutdown(ctx)");
        int workers = source.indexOf("RtWorkerPool.INSTANCE.shutdown()", terrain);
        int quiesce = source.indexOf("ctx.quiesceForOwnerShutdown()", workers);
        int entities = source.indexOf("RtEntities.INSTANCE.shutdown(ctx)", quiesce);
        int composite = source.indexOf("RtComposite.INSTANCE.destroy()", entities);
        int context = source.indexOf("ctx.destroy()", composite);

        assertTrue(terrain >= 0 && terrain < workers);
        assertTrue(workers < quiesce && quiesce < entities);
        assertTrue(entities < composite && composite < context);
    }

    @Test
    void executorStopsBeforeWaitAndFlushesBeforeFinalInfrastructureDestroy() throws Exception {
        String source = read("src/main/java/dev/comfyfluffy/caustica/rt/RtGpuExecutor.java");
        String quiesce = method(source, "public synchronized void quiesceForOwnerShutdown()",
                "/** Destroy executor infrastructure");
        int close = quiesce.indexOf("shutdownState.beginQuiescence()");
        int stop = quiesce.indexOf("jobs.add(STOP)");
        int join = quiesce.indexOf("thread.join()");
        int wait = quiesce.indexOf("ctx.waitIdle()");
        int mark = quiesce.indexOf("shutdownState.markQuiesced()");
        int flush = quiesce.indexOf("flushDestroysAfterDeviceIdle()");

        assertTrue(close >= 0 && close < stop && stop < join);
        assertTrue(join < wait && wait < mark && mark < flush);
        assertFalse(quiesce.contains("vkDestroyCommandPool"));

        String destroy = method(source, "public synchronized void destroyAfterQuiescence()",
                "/** Convenience composed shutdown");
        assertTrue(destroy.indexOf("shutdownState.shouldDestroyInfrastructure()")
                < destroy.indexOf("vkDestroyCommandPool"));
        assertFalse(destroy.contains("waitIdle"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
