package dev.comfyfluffy.caustica.rt.terrain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LodBuildSessionTest {
    private static final Path TERRAIN_SOURCE = Path.of(
            "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtLodTerrain.java");

    @Test
    void oldProxyRemainsWhileReplacementIsIncomplete() throws IOException {
        LodBuildSession session = new LodBuildSession();

        session.beginPacking();
        session.beginGpuBuilding();

        assertEquals(LodBuildSession.State.GPU_BUILDING, session.state());
        assertFalse(session.state() == LodBuildSession.State.FINAL);
        String source = Files.readString(TERRAIN_SOURCE);
        assertTrue(source.contains("new BuildSession(taskEpoch, revision, new ArrayDeque<>(plan), materials, current)"));
        assertTrue(source.contains("current = next;"));
    }

    @Test
    void progressiveCheckpointCanPublish() {
        LodBuildSession session = buildingSession();

        session.markCheckpointReady();
        session.publishCheckpoint();

        assertEquals(LodBuildSession.State.PUBLISHED, session.state());
    }

    @Test
    void finalCheckpointCanDropStaleAfterProgressivePublication() throws IOException {
        LodBuildSession session = buildingSession();

        session.markCheckpointReady();
        session.publishCheckpoint();
        session.beginPacking();
        session.beginGpuBuilding();
        session.markCheckpointReady();
        session.publishFinal();

        assertEquals(LodBuildSession.State.FINAL, session.state());
        String source = Files.readString(TERRAIN_SOURCE);
        assertTrue(source.contains(
                "session.workingEntries.keySet().removeIf(key -> !session.finalBatchKeys.contains(key));"));
        assertTrue(source.contains("publishBuildSession(ctx, true);"));
    }

    @Test
    void cancelledGenerationNeverPublishes() throws IOException {
        LodBuildSession session = buildingSession();
        session.cancel();

        assertEquals(LodBuildSession.State.CANCELLED, session.state());
        assertThrows(IllegalStateException.class, session::publishFinal);
        assertThrows(IllegalStateException.class, session::publishCheckpoint);
        String source = Files.readString(TERRAIN_SOURCE);
        assertTrue(source.contains("if (done.epoch != epoch)"));
        assertTrue(source.contains("session.lifecycle.cancel();"));
    }

    private static LodBuildSession buildingSession() {
        LodBuildSession session = new LodBuildSession();
        session.beginPacking();
        session.beginGpuBuilding();
        return session;
    }
}
