package dev.comfyfluffy.caustica.rt.lod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class LodProviderSelectorTest {
    @Test
    void validVoxySnapshotOwnsTheHorizonBeforeDhIsQueried() {
        LodMesh voxyMesh = mesh(1L);
        LodMesh dhMesh = mesh(2L);
        Source voxy = new Source(new LodMeshSnapshot(List.of(voxyMesh)));
        Source dh = new Source(new LodMeshSnapshot(List.of(dhMesh)));

        LodProviderSelector.Selection selection = new LodProviderSelector(voxy, dh).select();

        assertEquals(LodProviderSelector.Provider.VOXY, selection.provider());
        assertSame(voxyMesh, selection.snapshot().meshes().get(0));
        assertEquals(0, dh.snapshotCalls, "DH must not be selected or queried when Voxy is valid");
    }

    @Test
    void emptyVoxySnapshotFallsBackToDh() {
        LodMesh dhMesh = mesh(3L);
        Source voxy = new Source(new LodMeshSnapshot(List.of()));
        Source dh = new Source(new LodMeshSnapshot(List.of(dhMesh)));

        LodProviderSelector.Selection selection = new LodProviderSelector(voxy, dh).select();

        assertEquals(LodProviderSelector.Provider.DH, selection.provider());
        assertSame(dhMesh, selection.snapshot().meshes().get(0));
        assertEquals(1, dh.snapshotCalls);
    }

    @Test
    void emptySnapshotsDisableTheProvider() {
        LodProviderSelector.Selection selection = new LodProviderSelector(
                new Source(new LodMeshSnapshot(List.of())),
                new Source(new LodMeshSnapshot(List.of()))).select();

        assertEquals(LodProviderSelector.Provider.DISABLED, selection.provider());
        assertEquals(List.of(), selection.snapshot().meshes());
    }

    private static LodMesh mesh(long key) {
        return new LodMesh(key, key, 0, 0, 0, 16, 1, new byte[0], new byte[0]);
    }

    private static final class Source implements LodMeshSource {
        private final LodMeshSnapshot snapshot;
        private int snapshotCalls;

        private Source(LodMeshSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public LodMeshSnapshot snapshot() {
            snapshotCalls++;
            return snapshot;
        }

        @Override
        public long revision() {
            return 0L;
        }

        @Override
        public int renderDistanceChunks() {
            return 0;
        }

        @Override
        public void reset() {
        }
    }
}
