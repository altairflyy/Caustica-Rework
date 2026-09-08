# AER-062 result

## Pre-audit and runtime seam

`RtEntities.beginFrame(...)` already produced the exact per-frame entity segment
consumed by TLAS construction, but exposed it as `FrameEntities` backed by reused
mutable capture lists.

The runtime result is now an `EntitySceneContribution`. It snapshots the base,
dynamic-instance and BLAS lists while preserving the geometry-table address and
opaque graphics-use bookkeeping. Capture timing, BLAS recording, TLAS ordering and
post-submit lifetime marking remain at their existing call sites.

## Validation

- `EntitySceneContributionTest` and rewrite characterization: PASS.
- `validate-build.ps1`: PASS, covering V0, V1 and V2.
- V1: 191 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- V3: not required for this ownership-only contribution seam.
- Forbidden-change audit: no shader math, entity capture, BLAS/TLAS ordering,
  synchronization, resource lifetime, configuration, or hot-path `waitIdle` changes.
