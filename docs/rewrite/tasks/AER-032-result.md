# AER-032 result

## Pre-audit and runtime seam

The path-trace recording seam is the adjacent primary/indirect
`RtPipeline.trace` pair after TLAS and push-constant preparation. The pass will
own only these calls and their existing visibility barriers. Pipeline/SBT,
descriptor, TLAS, push-layout, and shader ownership remain unchanged.

## Implementation

- Added a production `PathTracePass` delegate.
- Moved the primary trace, primary-to-indirect barrier, indirect trace, and
  downstream visibility barrier into that pass.
- The pass executes at the original call-site after the unchanged TLAS and
  push-constant preparation.
- The same `RtPipeline`, command buffer, dimensions, push constants, pass
  indices, labels, and timing stages are used.

## Validation

- Targeted `FramePipelineTest` and `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS; characterization 7/7 and baseline exact 4/4.
- V2 `validate-build.ps1`: PASS with the configured DLSS/Vulkan SDKs.
- V3: not run for this wrapper extraction.
- `git diff --check`: PASS.
- Shader and binding ABI audit: zero shader changes; `WorldPushData`,
  `WorldPushConstantsData`, descriptors, trace dimensions, and raygen indices
  unchanged.
- Forbidden-change audit: no reconstruction, upscaler, LOD, tuning, resource
  ownership, or wait-idle changes.

## Outcome

`AER-032` is DONE with equivalent path-trace recording; `AER-033` is READY.
