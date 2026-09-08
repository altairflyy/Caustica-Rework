# AER-033 result

## Pre-audit and runtime seam

The reconstruction seam starts after the optional experimental NRD block and
ends before FSR/XeSS/native upscale. It contains the existing DLSS-RR branch
and the mutually exclusive SVGF fallback. NRD remains outside this pass and
unchanged.

## Implementation

- Added a production `ReconstructionPass` delegate.
- Moved the existing DLSS-RR evaluate branch and complete SVGF temporal/
  atrous branch into that pass.
- Returned only the existing downstream state: whether DLSS-RR completed,
  which image is the upscale source, and whether SVGF integrated jitter.
- Preserved the existing eligibility predicates, history parity, reset rules,
  barriers, labels, timing stages, and DLSS jitter sign.
- Left NRD outside the pass and unchanged; it remains experimental and cannot
  become a second reconstruction path.

## Validation

- Targeted `FramePipelineTest`, `SvgfResourcesTest`, and
  `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS; characterization 7/7 and baseline exact 4/4.
- V2 `validate-build.ps1`: PASS with configured DLSS/Vulkan SDKs.
- V3: not run for this wrapper extraction.
- `git diff --check`: PASS.
- Forbidden-change audit: zero shader/math, descriptor ABI, SVGF ownership,
  upscaler, LOD, tuning, or wait-idle changes.

## Outcome

`AER-033` is DONE with the same reconstruction backend selected under the
same conditions; `AER-034` is READY.
