package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RtGpuProfilerTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/comfyfluffy/caustica/rt/RtGpuProfiler.java");
    private static final Path COMPOSITE_SOURCE = Path.of("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");

    @Test
    void timestampDeltaHandlesQueueCounterWrap() {
        assertEquals(15L, RtGpuProfiler.timestampDelta(250L, 9L, 8));
        assertEquals(40L, RtGpuProfiler.timestampDelta(100L, 140L, 64));
    }

    @Test
    void timestampDeltaRejectsInvalidQueueCapabilities() {
        assertThrows(IllegalArgumentException.class, () -> RtGpuProfiler.timestampDelta(0L, 1L, 0));
        assertThrows(IllegalArgumentException.class, () -> RtGpuProfiler.timestampDelta(0L, 1L, 65));
    }

    @Test
    void aggregateUsesNearestRankP95AndStableRootFormatting() {
        StringBuilder output = new StringBuilder();
        RtGpuProfiler.appendStats(output, "primary", List.of(4.0, 1.0, 3.0, 2.0));
        assertEquals(" primary[min/avg/p95/max]=1.000/2.500/4.000/4.000ms", output.toString());
    }

    @Test
    void retrievalIsAvailabilityBasedAndNeverRequestsAWait() throws Exception {
        String source = Files.readString(SOURCE);
        assertEquals(false, source.contains("VK_QUERY_RESULT_WAIT_BIT"));
        assertEquals(false, source.contains("VK_QUERY_RESULT_PARTIAL_BIT"));
        assertEquals(false, source.contains("vkDeviceWaitIdle"));
        assertEquals(false, source.contains("vkQueueWaitIdle"));
        assertEquals(true, source.contains("VK_QUERY_RESULT_WITH_AVAILABILITY_BIT"));
        assertEquals(true, source.contains("GRAPHICS_RING_SIZE = 8"));
        assertEquals(true, source.contains("VK10.vkCmdWriteTimestamp"));
        assertEquals(false, source.contains("vkCmdWriteTimestamp2KHR"));
    }

    @Test
    void finalTimestampIsRecordedBeforeCommandBufferEnds() throws Exception {
        String source = Files.readString(COMPOSITE_SOURCE);
        int finish = source.indexOf("gpuProfile.finish();");
        int endCommandBuffer = source.indexOf("VK10.vkEndCommandBuffer(cmd)", finish);
        assertEquals(true, finish >= 0);
        assertEquals(true, endCommandBuffer > finish);
    }
}
