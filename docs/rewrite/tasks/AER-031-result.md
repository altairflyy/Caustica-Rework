# AER-031 result

## Pre-audit and runtime seam

`RtComposite.composite()` currently snapshots `TemporalState` and publishes
the frame jitter immediately before the active `FramePipeline` delegates to
the legacy recording path. These are state-only preparation operations and
form the minimal safe `PrepareFramePass` seam. Resource creation, capability
selection, reset delivery, and shader recording remain in their current
locations.

## Implementation

- Added `PrepareFramePass` as the first production `FramePipeline` pass.
- Moved `TemporalState.snapshot(frame)` and Frame Generation jitter
  publication from `RtComposite.composite()` into the preparation delegate.
- Kept the exact observable order:
  `FrameContext` creation, temporal snapshot, FG jitter publication, legacy
  recording.
- Kept resource creation, capability/backend selection, reset delivery, GPU
  resource ownership, and shader recording in their existing locations.
- Added a targeted pipeline-order test proving `Prepare` executes before
  `LegacyComposite` with the same immutable `FrameContext`.

## Validation

- Targeted `FramePipelineTest`, `TemporalStateTest`, and `FrameContextTest`:
  PASS.
- V1 `validate-fast.ps1`: PASS; characterization 7/7 and baseline exact 4/4.
- V2 `validate-build.ps1`: PASS with the configured DLSS/Vulkan SDKs.
- V3: not run for this extraction task.
- `git diff --check`: PASS.
- Forbidden-change audit: no shader, shader math, ReSTIR, SHaRC, SVGF,
  upscaler algorithm, LOD, tuning, or wait-idle change.

## Outcome

`AER-031` is DONE. Legacy call order and behavior remain unchanged;
`AER-032` is READY.
