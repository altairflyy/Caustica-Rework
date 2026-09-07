# AER-028 result

## Pre-audit and runtime seam

`RtLodTerrain` already retained the published proxy while a replacement was
planned and progressively built. Its implicit lifecycle crossed CPU packing,
GPU building, checkpoint publication, final publication, and abort paths.

## Plan

Formalize those transitions in a resource-free `LodBuildSession`, drive it
from the existing runtime seams, and preserve progressive checkpoints,
old-proxy retention, final stale-entry removal, and epoch cancellation.

## Implementation

Added the production-used, resource-free `LodBuildSession` lifecycle with
the explicit states `PLANNED`, `PACKING`, `GPU_BUILDING`,
`CHECKPOINT_READY`, `PUBLISHED`, `FINAL`, and `CANCELLED`. The existing
terrain build session drives those transitions at the real CPU pack, GPU
completion, progressive publish, final publish, and abort seams.

The published `current` proxy remains the input to an incomplete replacement;
only final publication removes entries absent from the final batch set. An
abort cancels the lifecycle and stale epoch results are discarded as before.
The state object owns no images, buffers, BLAS, tables, or proxy resources.

## Validation

- `LodBuildSessionTest`: PASS, all 4 required acceptance cases with
  production-seam characterization.
- `RtLodTerrainTest` and `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`, 159 tests with exact
  4/4 frozen failures; characterization 7/7.
- V2 `validate-build.ps1`: PASS, same baseline-equivalent test result.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no shader source, shader/math, tuning,
  AER-030+, AER-020, provider ownership, or resource-lifetime changes.
- V3: NOT AVAILABLE; no configured Vulkan smoke host.

State: DONE.
