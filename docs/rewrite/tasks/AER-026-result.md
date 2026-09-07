# AER-026 result

## Pre-audit and runtime seam

`RtLodTerrain.planCapturedLods(...)` previously called its private
`removeFullyCoveredCoarseMeshes(...)`. That method sorted captured meshes by
detail/width/key and recursively checked half-open square coverage. The same
rectangle semantics are also needed by progressive source eviction.

## Plan

Extract the coverage algorithm and its provider-neutral rectangle into
`LodCoverageResolver`, then use that shared resolver from both planning and
progressive eviction. Preserve ordering, key/version handling, negative
coordinates, boundary semantics, and all LOD planning limits.

## Implementation

`LodCoverageResolver.removeFullyCoveredCoarseMeshes(...)` now owns the
provider-neutral coverage decision. `RtLodTerrain` delegates its planning
call and its progressive stale-source eviction to the same resolver; no
second coverage algorithm or ownership path remains in terrain.

The original sort order, same-source-key exclusion, recursive half-open
coverage test, and long-bound arithmetic are unchanged. No provider,
meshing, batching, resource lifetime, shader, or math behavior was changed.

## Validation

- `LodCoverageResolverTest`: PASS, all 5 required cases.
- `RtLodTerrainTest` and `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`, 152 tests with exact
  4/4 frozen failures; characterization 7/7.
- V2 `validate-build.ps1`: PASS, same baseline-equivalent test result.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no shader source, shader/math, tuning,
  AER-027+, AER-020, or stash changes.
- V3: NOT AVAILABLE; no configured Vulkan smoke host.

State: DONE.
