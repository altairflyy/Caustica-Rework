# AER-063 result

## Pre-audit and runtime seam

`RtLodTerrain.appendInstances(...)` exposed a reused composite list that mixed the
published LOD proxy with terrain input. The published proxy itself is the correct
LOD authority; build-session internals remain private and are not scene inputs.

`RtLodTerrain.sceneContribution(...)` now snapshots only the currently published
LOD instances and their matching geometry-table address. `RtComposite` consumes
that contribution at the same frame seam and temporarily preserves the legacy
terrain-then-LOD ordering; central scene assembly remains reserved for AER-064.

## Validation

- `LodSceneContributionTest`, `RtLodTerrainTest`, `LodBuildSessionTest` and
  rewrite characterization: PASS.
- `validate-build.ps1`: PASS, covering V0, V1 and V2.
- V1: 192 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- V3: not required for this ownership-only contribution seam; the known DH/Voxy
  runtime qualification remains deferred as documented by the repository.
- Forbidden-change audit: no shader math, LOD selection/coverage/build lifecycle,
  BLAS/TLAS fields, synchronization, resource retirement, or hot-path `waitIdle` changes.
