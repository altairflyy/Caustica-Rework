# AER-035 result

## Pre-audit and runtime seam

The final pass begins at exposure metering after `rrOutput` becomes visible,
then performs the display transform, updates HDR-present state, and copies the
display image to the native target. Command-buffer submission and resource
lifetime bookkeeping remain the enclosing execution shell.

This final extraction also replaces the temporary monolithic legacy wrapper
with one ordered `FramePipeline` cursor so the production pass order is
declared and enforced in one place.

## Implementation

- Added a production `PostPresentPass` delegate.
- Moved exposure metering, its visibility barrier, display mapping, HDR state
  publication, native-target copy, and final barrier into the pass.
- Left command-buffer finalization/submission and resource-use bookkeeping in
  the enclosing execution shell at their original positions.
- Replaced the temporary `Prepare + LegacyComposite` pipeline with one ordered
  production pipeline containing:
  `PrepareFramePass`, `PathTracePass`, `ReconstructionPass`, `UpscalePass`,
  `PostPresentPass`.
- Added an incremental cursor that enforces declared pass order while allowing
  the unchanged legacy prerequisites between macro passes.
- Removed `LegacyCompositePass`; no dead or duplicate orchestration remains.

## Validation

- Targeted `FramePipelineTest` and `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS; characterization 7/7 and baseline exact 4/4.
- V2 `validate-build.ps1`: PASS with configured DLSS/Vulkan SDKs.
- V3: deferred to the GATE-3 audit; availability must be checked there.
- `git diff --check`: PASS.
- Order audit: exposure precedes display map, HDR state publication precedes
  native-target copy, and submission/history bookkeeping remains afterward.
- Forbidden-change audit: zero shader/math, reconstruction/upscale algorithm,
  resource ownership, LOD, tuning, Render Graph, or wait-idle changes.

## Outcome

`AER-035` is DONE. The explicit production macro order is now owned and
enforced by `FramePipeline`; GATE-3 remains pending formal audit.
