# AER-024 result

## Pre-audit and runtime seam

Before this task, `DistantHorizonsCompat.lodMeshesSnapshot()` contained the
Voxy-first/DH-fallback policy directly, even though both provider wrappers
already existed. The runtime seam is that facade method, plus its combined
revision, effective distance, and reset operations consumed by
`RtDistantHorizonsTerrain`.

## Implementation

Added `LodProviderSelector` as the single owner of provider selection. It
returns exactly one of `VOXY`, `DH`, or `DISABLED`: a non-empty Voxy source
snapshot wins; DH is queried only when Voxy is empty; two empty snapshots
disable the source. `DistantHorizonsCompat` now delegates the legacy runtime
surface to the selector, while DH/Voxy capture and reflection remain in their
own sources. The combined revision and effective distance retain legacy
behavior so provider transitions and existing proxy sizing remain observable.

No simultaneous provider publication, meshing rewrite, shader/math change,
GPU ownership change, or AER-025 rename is introduced.

## Validation

- `LodProviderSelectorTest`, `VoxyBridgeLodMeshSourceTest`,
  `DhLodMeshSourceTest`, `LodMeshContractTest`,
  `RtDistantHorizonsTerrainTest`, and `RtRewriteCharacterizationTest`: PASS.
- LOD-007 coverage: Voxy wins when valid, DH is queried only as fallback, and
  empty snapshots produce `DISABLED`.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`, exact 4/4 frozen
  failures; characterization 7/7.
- V2 `validate-build.ps1`: PASS, same baseline-equivalent test result.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no simultaneous provider publication, DH/Voxy
  meshing changes, shader source, shader/math, GPU ownership, tuning, AER-025+
  or stash changes.
- V3: NOT AVAILABLE; no configured Vulkan smoke host.

State: DONE.
