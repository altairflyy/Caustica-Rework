package dev.comfyfluffy.caustica.rt.graph;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AER-091 characterization: qualified replacements are the only reachable production paths. */
final class LegacyPathRemovalTest {
    @Test
    void productionNoLongerSelectsLegacyRewriteBranches() throws Exception {
        assertFalse(Files.exists(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rewrite/RewriteGates.java")));
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            long gateCallers = sources.filter(path -> path.toString().endsWith(".java"))
                    .map(LegacyPathRemovalTest::read)
                    .filter(source -> source.contains("RewriteGates."))
                    .count();
            assertTrue(gateCallers == 0, "production still selects a V2/legacy branch");
        }
    }

    @Test
    void graphAndBarrierEmittersContainNoLegacyFallback() throws Exception {
        String graph = read(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/graph/GraphExecution.java"));
        assertFalse(graph.contains("legacy.begin("));
        assertTrue(graph.contains("return new Cursor(frame);"));

        for (String file : new String[]{"DenoiserBarriers.java", "UpscalerBarriers.java",
                "PostImageBarriers.java", "PathTraceBarriers.java"}) {
            String emitter = read(Path.of("src/main/java/dev/comfyfluffy/caustica/rt/graph", file));
            assertFalse(emitter.contains("VulkanCommandEncoder.memoryBarrier("), file);
            assertFalse(emitter.contains("boolean generated"), file);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
