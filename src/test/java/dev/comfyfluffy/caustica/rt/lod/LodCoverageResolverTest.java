package dev.comfyfluffy.caustica.rt.lod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LodCoverageResolverTest {
    @Test
    void completeCoverageRemovesTheCoarseParent() {
        LodMesh parent = mesh(1L, 1L, 0, 0, 16, 16);

        List<LodMesh> result = LodCoverageResolver.removeFullyCoveredCoarseMeshes(List.of(
                parent,
                mesh(2L, 2L, 0, 0, 8, 8), mesh(3L, 2L, 8, 0, 8, 8),
                mesh(4L, 2L, 0, 8, 8, 8), mesh(5L, 2L, 8, 8, 8, 8)));

        assertFalse(result.contains(parent));
    }

    @Test
    void partialCoverageKeepsTheCoarseParent() {
        LodMesh parent = mesh(1L, 1L, 0, 0, 16, 16);

        List<LodMesh> result = LodCoverageResolver.removeFullyCoveredCoarseMeshes(List.of(
                parent,
                mesh(2L, 2L, 0, 0, 8, 8), mesh(3L, 2L, 8, 0, 8, 8),
                mesh(4L, 2L, 0, 8, 8, 8)));

        assertTrue(result.contains(parent));
    }

    @Test
    void sameSourceKeyWithMixedVersionsIsKeptSafe() {
        LodMesh coarse = mesh(7L, 1L, 0, 0, 16, 16);
        LodMesh newerSameSource = mesh(7L, 2L, 0, 0, 8, 8);

        List<LodMesh> result = LodCoverageResolver.removeFullyCoveredCoarseMeshes(
                List.of(coarse, newerSameSource));

        assertTrue(result.contains(coarse));
        assertTrue(result.contains(newerSameSource));
    }

    @Test
    void negativeCoordinatesHaveTheSameCoverageSemantics() {
        LodMesh parent = mesh(10L, 1L, -16, -16, 16, 16);

        List<LodMesh> result = LodCoverageResolver.removeFullyCoveredCoarseMeshes(List.of(
                parent,
                mesh(11L, 2L, -16, -16, 8, 8), mesh(12L, 2L, -8, -16, 8, 8),
                mesh(13L, 2L, -16, -8, 8, 8), mesh(14L, 2L, -8, -8, 8, 8)));

        assertFalse(result.contains(parent));
    }

    @Test
    void boundaryTouchOnlyDoesNotCountAsCoverage() {
        LodMesh parent = mesh(20L, 1L, 0, 0, 16, 16);
        LodMesh touching = mesh(21L, 2L, 16, 0, 8, 8);

        List<LodMesh> result = LodCoverageResolver.removeFullyCoveredCoarseMeshes(
                List.of(parent, touching));

        assertTrue(result.contains(parent));
    }

    private static LodMesh mesh(long key, long version, int x, int z, int width, int detailWidth) {
        return new LodMesh(key, version, x, 0, z, width, detailWidth, new byte[0], new byte[0]);
    }
}
