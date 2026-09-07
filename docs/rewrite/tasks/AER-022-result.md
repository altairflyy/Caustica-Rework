# AER-022 result

## Pre-audit and runtime seam

The existing DH upload mixin remains the capture point:
`DistantHorizonsLodBufferMixin` calls `DistantHorizonsCompat.captureLodBuffers(...)`.
The compatibility layer retains the DH CPU mesh map, world scoping, source
revision, and reflective DH render-distance lookup. Its old public snapshot is
hybrid because it applies the existing Voxy-first fallback policy.

`DhLodMeshSource` therefore wraps new DH-only accessors in
`DistantHorizonsCompat`. The legacy hybrid facade now consumes this source for
its DH fallback, preserving Voxy-first selection. No DH meshing is rewritten,
and no GPU ownership moves in this task.

## Implementation

Added `DhLodMeshSource` as the production wrapper for captured DH meshes. The
legacy hybrid facade now obtains its DH fallback snapshot, revision, render
distance, and reset through this source. DH-only accessors keep Voxy out of the
source contract, while `DistantHorizonsLodBufferMixin` remains the unchanged
capture hook. The source has no GPU/resource ownership and does not alter DH
meshing.

The existing Voxy-first behavior remains byte/order/policy equivalent: a
non-empty Voxy snapshot is returned before the DH source is queried; otherwise
the captured DH snapshot is used.

## Validation

- `DhLodMeshSourceTest`, `LodMeshContractTest`,
  `RtDistantHorizonsTerrainTest`, and `RtRewriteCharacterizationTest`: PASS,
  12/12 targeted tests; characterization 7/7.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`, exact 4/4 frozen
  failures.
- V2 `validate-build.ps1`: PASS, same baseline-equivalent test result.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no DH meshing rewrite, shader source,
  shader/math, GPU ownership, tuning, AER-023+ or stash changes.
- V3: NOT AVAILABLE; no configured Vulkan smoke host.

State: DONE.
