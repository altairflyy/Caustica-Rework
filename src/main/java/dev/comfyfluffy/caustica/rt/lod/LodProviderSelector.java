package dev.comfyfluffy.caustica.rt.lod;

import java.util.List;
import java.util.Objects;

/**
 * Selects the single provider that owns the current distant-horizon snapshot.
 *
 * <p>A non-empty Voxy snapshot wins. DH is queried only when Voxy has no valid
 * snapshot; an empty result is the disabled state. The selector owns this
 * policy, while providers retain their own optional-compatibility boundaries.
 * </p>
 */
public final class LodProviderSelector {
    public enum Provider {
        VOXY,
        DH,
        DISABLED
    }

    public record Selection(Provider provider, LodMeshSnapshot snapshot) {
        public Selection {
            provider = Objects.requireNonNull(provider, "provider");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private final LodMeshSource voxySource;
    private final LodMeshSource dhSource;

    public LodProviderSelector() {
        this(new VoxyBridgeLodMeshSource(), new DhLodMeshSource());
    }

    LodProviderSelector(LodMeshSource voxySource, LodMeshSource dhSource) {
        this.voxySource = Objects.requireNonNull(voxySource, "voxySource");
        this.dhSource = Objects.requireNonNull(dhSource, "dhSource");
    }

    public Selection select() {
        LodMeshSnapshot voxy = voxySource.snapshot();
        if (!voxy.meshes().isEmpty()) {
            return new Selection(Provider.VOXY, voxy);
        }

        LodMeshSnapshot dh = dhSource.snapshot();
        if (!dh.meshes().isEmpty()) {
            return new Selection(Provider.DH, dh);
        }
        return new Selection(Provider.DISABLED, new LodMeshSnapshot(List.of()));
    }

    public LodMeshSnapshot snapshot() {
        return select().snapshot();
    }

    /** Preserve the legacy combined source revision so a provider transition is observed promptly. */
    public long revision() {
        long dh = dhSource.revision();
        long voxy = voxySource.revision();
        return dh ^ Long.rotateLeft(voxy, 29);
    }

    /** Preserve the legacy effective distance while selection owns snapshot ownership. */
    public int renderDistanceChunks() {
        return Math.max(voxySource.renderDistanceChunks(), dhSource.renderDistanceChunks());
    }

    public void reset() {
        voxySource.reset();
        dhSource.reset();
    }
}
