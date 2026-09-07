# AER-023 result

## Pre-audit and runtime seam

`VoxyCompat` already isolates the optional provider behind reflection to
`me.cortex.voxy.client.compat.CausticaBridge`. Discovery requires
`available()` plus the existing poll/revision/render-distance/reset and
configuration signatures; any missing class, method, or incompatible bridge
leaves `API` unavailable. Calls then return empty/zero/false or clear local
state without crashing the renderer.

`VoxyBridgeLodMeshSource` wraps that existing compatibility surface. The
legacy DH facade uses the source for Voxy active-state selection,
snapshot/revision/render-distance, and reset, while the existing render-thread
poll remains in `VoxyCompat`. No standard-Voxy compatibility is claimed and no
Voxy meshing or GPU ownership is moved.

## Implementation

Added `VoxyBridgeLodMeshSource` as the production wrapper for the existing
`VoxyCompat` boundary. The DH facade now obtains Voxy active state,
snapshot/revision, render distance, and reset through this source; render-
thread polling remains in the existing compatibility class because it is not
part of the `LodMeshSource` contract.

The bridge remains explicitly Caustica-specific. Missing or incompatible
`me.cortex.voxy.client.compat.CausticaBridge` leaves the source unavailable and
its snapshot empty, without a renderer crash. No compatibility claim is made
for standard Voxy.

## Validation

- `VoxyBridgeLodMeshSourceTest`, `DhLodMeshSourceTest`,
  `LodMeshContractTest`, `RtDistantHorizonsTerrainTest`, and
  `RtRewriteCharacterizationTest`: PASS, 12/12 targeted tests;
  characterization 7/7.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`, exact 4/4 frozen
  failures.
- V2 `validate-build.ps1`: PASS, same baseline-equivalent test result.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no standard-Voxy claim, shader source,
  shader/math, GPU ownership, tuning, AER-024+ or stash changes.
- V3: NOT AVAILABLE; no configured Vulkan smoke host.

State: DONE.
