# AER-030 result

## Pre-audit and runtime seam

The current macro-frame work is concentrated in `RtComposite`. The smallest
safe seam is after the existing resource/output/motion preparation and before
the current GPU recording path. `FramePipeline` therefore runs one
`LegacyCompositePass`, which delegates to the unchanged `recordFrame` path.
`RtComposite` remains the owner of resources, backend selection, bindings, and
recording state.

## Implementation

- Added immutable linear `FramePipeline` and `FramePass` contracts.
- Added `LegacyCompositePass` as the only active pass.
- Wired `RtComposite.composite()` to snapshot the existing frame data and
  execute `framePipeline.execute(frameContext)`.
- Factored the existing backend-priority and jitter calculation into one
  per-frame `FrameInputs` value so jitter is prepared exactly once.
- Kept the existing `recordFrame` implementation as the pass delegate; no
  resources, shader bindings, shader math, backend algorithms, or present
  ordering moved.

## Validation

- Targeted `FramePipelineTest` + `FrameContextTest`: PASS.
- Full Gradle tests: baseline-equivalent; the only failures are the frozen 4
  shader regression failures.
- V1 `validate-fast.ps1`: PASS; characterization 7/7, baseline exact 4/4.
- V2 `validate-build.ps1`: PASS; build completed with local DLSS/Vulkan SDKs.
- V3: not run; no runtime smoke was requested/available in this task.
- `git diff --check`: PASS.
- Forbidden-change audit: shader files unchanged; no shader/math, ReSTIR,
  SHaRC, SVGF, upscaler, LOD, config-tuning, or wait-idle changes.

## Outcome

`AER-030` is DONE. Behavior is unchanged and `AER-031` is READY.
