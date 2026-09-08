# AER-061 result

## Pre-audit and runtime seam

`RtTerrain.staticInstances()` exposed the mutable published section-table list
directly to `RtComposite`. The list already contained the exact ordered instance
descriptors expected by TLAS construction.

`RtTerrain.sceneContribution()` now returns a `TerrainSceneContribution` that
freezes that ordered list for the frame. `RtComposite` consumes the contribution
at the same point immediately before LOD composition and entity capture. Instance
transforms, BLAS addresses, custom indices, masks, SBT offsets and ordering are
unchanged.

No light/material view, entity/LOD contribution or scene assembly is moved in
this task.

## Validation

- `TerrainSceneContributionTest` and the rewrite characterization suite: PASS.
- `validate-build.ps1`: PASS, covering V0, V1 and V2 without a duplicate V1 run.
- V1: 190 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- V3: not required for this ownership-only scene contribution seam.
- Forbidden-change audit: no shader math, TLAS instance fields, resource lifetime,
  synchronization, or hot-path `waitIdle` changes.
