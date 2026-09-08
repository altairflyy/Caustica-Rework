# AER-040 result

## Pre-audit and runtime seam

Published GPU owners are currently retired by direct calls to the two
`RtGpuExecutor.retireAfterGraphics(...)` overloads. The executor owns the
graphics timeline, destroy queue, wake-up, completion query, and destruction
timing; those responsibilities must remain there.

The conservative seam is a per-`RtContext` `DeferredDeletionQueue` facade that
forwards the exact `GraphicsUse` or `TrackedGraphicsUse` token and destruction
callback to the existing executor. Unpublished build-result retirement is a
different lifetime domain and remains on `RtGpuExecutor`.

## Implementation

- Added the facade at `rt/gpu/DeferredDeletionQueue.java` with overloads for
  the existing immutable and tracked graphics-use token types.
- Constructed and exposed one facade from the production `RtContext`.
- Routed every published graphics-retirement call-site through that facade.
- Kept all queue storage, timeline signaling/querying, wake-up, flushing, and
  shutdown behavior unchanged in `RtGpuExecutor`.
- Kept `retireUnpublished(...)` on the executor because it does not carry a
  published graphics-use token and is outside this facade's contract.
- Added focused delegation tests that prove token and callback identity, plus
  a production-adoption check that rejects direct facade bypasses.

## Validation

- `DeferredDeletionQueueTest`: PASS (3/3).
- V1 `validate-fast.ps1`: PASS; 169 tests, characterization 7/7 and baseline
  exact 4/4.
- V2 `validate-build.ps1`: PASS with configured DLSS/Vulkan SDKs and the same
  exact baseline comparison.
- V3: NOT REQUIRED for this facade-only task.
- `git diff --check`: PASS.
- Runtime adoption audit: all ten published-retirement call-sites use
  `RtContext.deferredDeletionQueue()`; only the facade calls the executor's two
  `retireAfterGraphics(...)` overloads.
- Token/timing audit: no token is copied or transformed and the facade adds no
  storage, polling, scheduling, retry, or callback execution.
- Forbidden-change audit: no shader/math, algorithm, resource destruction,
  timeline, synchronization, LOD policy, tuning, or wait-idle changes.

## Outcome

`AER-040` is DONE. `RtGpuExecutor` remains the sole destruction-queue and
timeline authority; `AER-041` is READY and was not started.

## Characterization clarification

The production-adoption check rejects calls whose receiver is the legacy
`RtGpuExecutor`, rather than rejecting every higher-level facade that exposes a
same-named operation. This preserves the original invariant while allowing an
ownership facade to delegate through `DeferredDeletionQueue`; the exact token
and callback delegation checks remain unchanged.
