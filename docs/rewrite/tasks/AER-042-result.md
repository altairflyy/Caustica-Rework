# AER-042 result

## Pre-audit and runtime seam

`RtAccel` currently owns the Vulkan implementation and exact geometry/flag
construction for BLAS build, update/refit, compaction, and TLAS build. AER-042
must not move or rewrite that implementation.

The conservative runtime seam is one `AccelerationStructureManager` owned by
`RtContext`. Its methods forward existing domain objects and arguments directly
to `RtAccel`. The frame TLAS prepare/record calls are the initial production
adoption point; LOD, terrain, and entity ownership migrations remain deferred to
AER-043, AER-044, and AER-045 respectively.

## Implementation

- Add the minimal `prepareStaticBlas`, `prepareUpdatableBlas`, `refit`,
  `compact`, `buildTlas`, and `retire` facade API.
- Keep lifecycle helpers as direct delegates so later migrations need no
  semantic translation.
- Construct/expose one manager from production `RtContext`.
- Route frame TLAS preparation and recording through the manager without
  changing the ring, token, descriptor publication, command order, or barrier.
- Add focused structural tests for direct delegation and runtime adoption.

## Validation

- `AccelerationStructureManagerTest`: PASS (2/2).
- Combined `AccelerationStructureManagerTest` and
  `DeferredDeletionQueueTest`: PASS (5/5).
- V1 `validate-fast.ps1`: PASS; 171 tests, characterization 7/7 and frozen
  baseline exact 4/4.
- V2 `validate-build.ps1`: PASS with configured DLSS/Vulkan SDKs and the same
  exact baseline comparison.
- `git diff --check`: PASS.
- Geometry/flags audit: all Vulkan build flags and geometry construction remain
  in `RtAccel`; the facade forwards the original arguments unchanged.
- Command-order audit: TLAS prepare, descriptor publication, build recording,
  and memory barrier remain in their original order.
- Forbidden-change audit: no shader/math, resource lifetime, LOD policy,
  algorithm, tuning, or wait-idle changes.

## Outcome

`AER-042` is DONE. The manager is a production-owned facade over the unchanged
`RtAccel` implementation and deferred-retirement boundary. `AER-043` is READY
and was not started.
