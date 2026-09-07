package dev.comfyfluffy.caustica.rt.lod;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves overlapping LOD squares without removing a coarse mesh until finer
 * geometry covers its complete area.
 *
 * <p>Coverage uses half-open integer rectangles, matching the existing DH
 * planning behavior. Source versions are intentionally not merged or
 * rewritten here; this resolver only decides spatial coverage.</p>
 */
public final class LodCoverageResolver {
    private LodCoverageResolver() {
    }

    /**
     * Keep a coarse mesh until finer active meshes cover its full square.
     *
     * @param meshes captured provider-neutral meshes
     * @return a new list with fully covered coarse meshes removed
     */
    public static ArrayList<LodMesh> removeFullyCoveredCoarseMeshes(List<LodMesh> meshes) {
        ArrayList<LodMesh> ordered = new ArrayList<>(meshes);
        ordered.sort((a, b) -> {
            int byDetail = Integer.compare(a.dataPointWidth(), b.dataPointWidth());
            if (byDetail != 0) return byDetail;
            int byWidth = Integer.compare(a.width(), b.width());
            return byWidth != 0 ? byWidth : Long.compareUnsigned(a.key(), b.key());
        });
        ArrayList<LodMesh> selected = new ArrayList<>(ordered.size());
        ArrayList<CoverageRect> selectedRects = new ArrayList<>(ordered.size());
        for (LodMesh mesh : ordered) {
            CoverageRect rect = new CoverageRect(mesh.key(), mesh.originX(), mesh.originZ(), mesh.width(),
                    mesh.dataPointWidth());
            if (!fullyCoveredBy(rect, selectedRects)) {
                selected.add(mesh);
                selectedRects.add(rect);
            }
        }
        return selected;
    }

    public static boolean fullyCoveredBy(CoverageRect target, List<CoverageRect> candidates) {
        ArrayList<CoverageRect> relevant = new ArrayList<>();
        for (CoverageRect candidate : candidates) {
            if (candidate.key == target.key) continue;
            boolean finer = candidate.detailWidth < target.detailWidth
                    || (candidate.detailWidth == target.detailWidth && candidate.width < target.width);
            if (finer && intersects(target, candidate)) relevant.add(candidate);
        }
        return !relevant.isEmpty() && coveredSquare(target.x, target.z, target.width, relevant);
    }

    private static boolean coveredSquare(int x, int z, int width, List<CoverageRect> candidates) {
        for (CoverageRect candidate : candidates) {
            if (contains(candidate, x, z, width)) return true;
        }
        if (width <= 1) return false;
        int first = width / 2;
        int second = width - first;
        return coveredSquarePart(x, z, first, first, candidates)
                && coveredSquarePart(x + first, z, second, first, candidates)
                && coveredSquarePart(x, z + first, first, second, candidates)
                && coveredSquarePart(x + first, z + first, second, second, candidates);
    }

    private static boolean coveredSquarePart(int x, int z, int width, int depth,
                                             List<CoverageRect> candidates) {
        if (width <= 0 || depth <= 0) return true;
        ArrayList<CoverageRect> relevant = new ArrayList<>();
        for (CoverageRect candidate : candidates) {
            if (intersects(candidate, x, z, width, depth)) relevant.add(candidate);
        }
        if (relevant.isEmpty()) return false;
        if (width == depth) return coveredSquare(x, z, width, relevant);
        // DH section widths are powers of two, but keep a safe rectangular fallback for malformed metadata.
        if (width > depth) {
            int first = width / 2;
            return coveredSquarePart(x, z, first, depth, relevant)
                    && coveredSquarePart(x + first, z, width - first, depth, relevant);
        }
        int first = depth / 2;
        return coveredSquarePart(x, z, width, first, relevant)
                && coveredSquarePart(x, z + first, width, depth - first, relevant);
    }

    private static boolean contains(CoverageRect outer, int x, int z, int width) {
        long outerEndX = (long) outer.x + outer.width;
        long outerEndZ = (long) outer.z + outer.width;
        return outer.x <= x && outer.z <= z
                && outerEndX >= (long) x + width && outerEndZ >= (long) z + width;
    }

    private static boolean intersects(CoverageRect a, CoverageRect b) {
        return intersects(a, b.x, b.z, b.width, b.width);
    }

    private static boolean intersects(CoverageRect a, int x, int z, int width, int depth) {
        return (long) a.x < (long) x + width && (long) x < (long) a.x + a.width
                && (long) a.z < (long) z + depth && (long) z < (long) a.z + a.width;
    }

    /** Provider-neutral rectangle used by both planning and progressive eviction. */
    public record CoverageRect(long key, int x, int z, int width, int detailWidth) {
    }
}
