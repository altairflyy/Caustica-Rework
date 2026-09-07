# AER-027 result

## Pre-audit and runtime seam

`RtLodTerrain.planCapturedLods(...)` currently owns the LOD planning loop.
Inside that loop, the provider-neutral work is the unchanged-source reuse
decision, bounded byte slicing, batch grouping, and deterministic `batchKey`
generation. GPU preparation, packing, decoding, build-session lifetime, and
publication remain terrain responsibilities.

## Plan

Extract that provider-neutral planning into `LodBatchPlanner`, keep snapshot
selection and the GPU-facing `PlannedBatch` adapter in `RtLodTerrain`, and
preserve the existing quad limit, batch keys, slice order, reuse predicate,
and rebuild behavior.

## Implementation

Added the runtime-used `LodBatchPlanner`. It owns the unchanged-source
version decision, opaque/transparent 64-byte slicing, bounded grouping at
`MAX_BUILD_QUADS = 131072`, and the legacy deterministic `batchKey` function.
`RtLodTerrain` delegates source planning and adapts the provider-neutral slice
plans to its existing GPU build records; packing, decoding, build-session
lifetime, and publication remain unchanged.

## Validation

- `LodBatchPlannerTest`: PASS, reuse, ordered mixed-pass grouping, and exact
  quad-limit subdivision.
- `LodCoverageResolverTest`, `RtLodTerrainTest`, and
  `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`, 155 tests with exact
  4/4 frozen failures; characterization 7/7.
- V2 `validate-build.ps1`: PASS, same baseline-equivalent test result.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no shader source, shader/math, tuning,
  AER-028+, AER-020, provider ownership, or resource-lifetime changes.
- V3: NOT AVAILABLE; no configured Vulkan smoke host.

State: DONE.
