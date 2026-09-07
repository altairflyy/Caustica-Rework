package dev.comfyfluffy.caustica.rt.lod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LodMeshContractTest {
    @Test
    void snapshotPreservesReferenceMeshOrderAndPayload() {
        byte[] opaque = {1, 2, 3};
        byte[] transparent = {4, 5};
        LodMesh mesh = new LodMesh(7L, 11L, 12, 34, 56, 16, 4, opaque, transparent);

        LodMeshSnapshot snapshot = new LodMeshSnapshot(List.of(mesh));

        assertEquals(List.of(mesh), snapshot.meshes());
        assertSame(opaque, snapshot.meshes().get(0).opaque());
        assertSame(transparent, snapshot.meshes().get(0).transparent());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.meshes().add(mesh));
    }

    @Test
    void sourceContractExposesOnlyReferenceOperations() {
        LodMeshSnapshot snapshot = new LodMeshSnapshot(List.of());
        TestSource source = new TestSource(snapshot, 19L, 128);

        assertSame(snapshot, source.snapshot());
        assertEquals(19L, source.revision());
        assertEquals(128, source.renderDistanceChunks());
        source.reset();
        assertEquals(1, source.resetCount);
    }

    private static final class TestSource implements LodMeshSource {
        private final LodMeshSnapshot snapshot;
        private final long revision;
        private final int renderDistanceChunks;
        private int resetCount;

        private TestSource(LodMeshSnapshot snapshot, long revision, int renderDistanceChunks) {
            this.snapshot = snapshot;
            this.revision = revision;
            this.renderDistanceChunks = renderDistanceChunks;
        }

        @Override
        public LodMeshSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public long revision() {
            return revision;
        }

        @Override
        public int renderDistanceChunks() {
            return renderDistanceChunks;
        }

        @Override
        public void reset() {
            resetCount++;
        }
    }
}
