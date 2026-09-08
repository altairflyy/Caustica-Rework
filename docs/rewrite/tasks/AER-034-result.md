# AER-034 result

## Pre-audit and runtime seam

The upscale seam starts with the existing FSR branch after reconstruction,
continues through XeSS, and ends with the native fallback plus the barrier
that exposes `rrOutput` to post-processing. The existing `rrDone` chain is the
single authority for mutual exclusion.

## Implementation

- Added a production `UpscalePass` delegate.
- Moved FSR, XeSS, and native fallback recording into the pass as one mutually
  exclusive slot.
- Passed the existing reconstruction outcome, source image, SVGF result, and
  NRD result into the pass without recomputing backend selection.
- Preserved FSR/XeSS teleport reset timing, camera tracking, jitter signs,
  converged-input XeSS jitter suppression, fallback blit, labels, timing, and
  the final visibility barrier.

## Validation

- Targeted `FramePipelineTest`, `TemporalStateTest`, and
  `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS; characterization 7/7 and baseline exact 4/4.
- V2 `validate-build.ps1`: PASS with configured DLSS/Vulkan SDKs.
- V3: not run for this wrapper extraction.
- `git diff --check`: PASS.
- Mutual-exclusion audit: the same `rrDone` chain remains authoritative and
  branch priority is unchanged.
- Forbidden-change audit: zero shader/math, backend algorithm, resource
  ownership, LOD, tuning, or wait-idle changes.

## Outcome

`AER-034` is DONE with equivalent upscale selection; `AER-035` is READY.
