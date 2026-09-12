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

    /** Conservative CPU-side result used before a DH BLAS is inserted into the frame TLAS. */
    public enum VanillaCoverage {
        FULL,
        PARTIAL,
        NONE
    }

    @FunctionalInterface
    public interface SectionReadiness {
        boolean isReady(int sectionX, int sectionY, int sectionZ);
    }

    @FunctionalInterface
    public interface SectionWindow {
        boolean contains(int sectionX, int sectionY, int sectionZ);
    }

    /**
     * Resolve a conservative 3-D vanilla replacement state for one spatial DH tile.  Anything outside
     * the current readiness window is PARTIAL (never FULL), while NONE is reserved for an entirely
     * in-window tile with no authoritative ready section.  A bounded scan prevents malformed or very
     * tall provider metadata from becoming a render-thread hitch; the safe result is PARTIAL.
     */
    public static VanillaCoverage resolveVanillaCoverage(CoverageRect tile,
                                                         SectionReadiness readiness,
                                                         SectionWindow window) {
        int minSectionX = Math.floorDiv(tile.x(), 16);
        int minSectionZ = Math.floorDiv(tile.z(), 16);
        int maxSectionX = Math.floorDiv(Math.addExact(tile.x(), Math.max(0, tile.width() - 1)), 16);
        int maxSectionZ = Math.floorDiv(Math.addExact(tile.z(), Math.max(0, tile.width() - 1)), 16);
        int minSectionY;
        int maxSectionY;
        if (!tile.hasVerticalBounds()) {
            return VanillaCoverage.PARTIAL;
        }
        minSectionY = Math.floorDiv(tile.minY(), 16);
        maxSectionY = Math.floorDiv(Math.addExact(tile.maxY() - 1, 0), 16);
        long cells = (long) (maxSectionX - minSectionX + 1)
                * (maxSectionY - minSectionY + 1)
                * (maxSectionZ - minSectionZ + 1);
        if (cells <= 0L || cells > 1_000_000L) {
            return VanillaCoverage.PARTIAL;
        }
        boolean anyReady = false;
        boolean anyMissing = false;
        boolean outside = false;
        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            for (int sz = minSectionZ; sz <= maxSectionZ; sz++) {
                for (int sx = minSectionX; sx <= maxSectionX; sx++) {
                    if (!window.contains(sx, sy, sz)) {
                        outside = true;
                        anyMissing = true;
                    } else if (readiness.isReady(sx, sy, sz)) {
                        anyReady = true;
                    } else {
                        anyMissing = true;
                    }
                }
            }
        }
        if (!anyMissing) return VanillaCoverage.FULL;
        if (!anyReady && !outside) return VanillaCoverage.NONE;
        return VanillaCoverage.PARTIAL;
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
    public record CoverageRect(long key, int x, int z, int width, int detailWidth,
                               int minY, int maxY) {
        public CoverageRect(long key, int x, int z, int width, int detailWidth) {
            this(key, x, z, width, detailWidth, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        public CoverageRect {
            if (width < 0) throw new IllegalArgumentException("width must be non-negative");
            if (maxY < minY) throw new IllegalArgumentException("maxY must be >= minY");
        }

        public boolean hasVerticalBounds() {
            return minY != Integer.MIN_VALUE && maxY != Integer.MAX_VALUE && maxY > minY;
        }

        public int maxX() { return Math.addExact(x, width); }
        public int maxZ() { return Math.addExact(z, width); }
    }
}
