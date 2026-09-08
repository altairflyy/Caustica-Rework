# AER-046 result

## Pre-audit

All Vulkan acceleration-structure handles are created and idempotently destroyed
inside `RtAccel`, making it the complete observation seam for live AS count and
live BLAS backing bytes. `RtGpuExecutor.destroyJobs` is the real deferred destroy
queue for both published and unpublished resources.

Add read-only counters at those authorities and sample them once per frame through
the existing opt-in frame statistics. Add an assertion against counter underflow
and characterize the manager boundary across LOD, terrain and entity callers.

## Validation

- Targeted `AccelerationStructureManagerTest`, `DeferredDeletionQueueTest`, and
  `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS, baseline-equivalent exact 4/4 canonical
  failures; characterization 7/7 PASS; 176 tests executed.
- V2 `validate-build.ps1`: PASS with the same exact baseline comparison.
- V0 `git diff --check`: PASS (line-ending conversion warnings only).
- Runtime smoke / Vulkan validation: not part of task-level validation; deferred
  to the GATE-4 closure audit.

## Acceptance evidence

- `RtAccel` is the common construction/destruction seam for all live BLAS/TLAS
  handles and now exposes real live-AS and live-BLAS-byte counters.
- `RtGpuExecutor.destroyJobs` remains the real queue authority and exposes total
  depth plus published-resource retirement depth without changing scheduling.
- `AccelerationStructureManager.recordDiagnostics(...)` samples all four
  required values once per frame through the existing opt-in `RtFrameStats`.
- Counter underflow is asserted after idempotent AS destruction.
- Production characterization rejects migrated critical AS destruction that
  bypasses `AccelerationStructureManager`; `TlasRing` internal slot cleanup and
  idle teardown remain the documented aggregate-owner exception.
- No shader, rendering math, tuning, feature-selection, or hot-path wait-idle
  changes were made.
