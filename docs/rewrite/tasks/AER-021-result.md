# AER-021 result

## Pre-audit and contract seam

The real LOD behavior is already provider-neutral at the captured-mesh
boundary. `DistantHorizonsCompat.lodMeshesSnapshot()` returns the currently
selected DH/Voxy `List<LodMesh>`, `lodRevision()` exposes the combined source
revision, and `renderDistanceChunks()` exposes the effective distance. Voxy
provides the same snapshot, revision, distance, and reset operations through
`VoxyCompat`. `RtDistantHorizonsTerrain` remains on those legacy static APIs;
this contract task does not rewire that runtime path.

The contract therefore uses a no-argument snapshot and keeps revision, render
distance, and reset as separate operations. `LodMeshSnapshot` preserves the
captured mesh order and payload while making the returned mesh list immutable.
No provider-specific API, resource ownership, shader/math behavior, or runtime
policy is introduced.

## Implementation

Added `LodMeshSnapshot` and `LodMeshSource` under `rt/lod`. The snapshot
freezes the real provider mesh list while preserving mesh order and captured
byte-array payloads. The source contract exposes exactly the operations
already represented by DH/Voxy: snapshot, revision, effective render
distance, and reset. No provider implementation or runtime adoption is part
of this `CONTRACT_ONLY` task; those remain for AER-022/AER-023/AER-024.

## Validation

- `LodMeshContractTest`: PASS.
- `RtDistantHorizonsTerrainTest`: PASS.
- `RtRewriteCharacterizationTest`: PASS, 7/7.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`, exact 4/4 frozen
  failures.
- V2 `validate-build.ps1`: PASS, same baseline-equivalent test result.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no provider runtime wiring, shader source,
  shader/math, resource ownership, tuning, AER-022+ or stash changes.
- V3: NOT AVAILABLE; no runtime smoke host was configured for this contract
  task.

State: DONE.
