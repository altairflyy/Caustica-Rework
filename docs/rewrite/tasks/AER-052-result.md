# AER-052 result

## Pre-audit and runtime seam

`RtDlssRr` already owns the NGX runtime handle, feature dimensions, quality and
preset materialization, fresh-feature history reset and permanent failure
latch. `RtComposite` directly called all of those operations from allocation,
selection, reconstruction and teardown seams.

The conservative integration is a production adapter around that existing
owner. It accepts only DLSS-RR inputs and preserves the exact feature setup,
guide order, jitter sign, matrices, output fallback and lifecycle calls.

## Validation

- Targeted `DlssRrReconstructionBackendTest`, `ReconstructionBackendTest` and
  `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS, baseline-equivalent exact 4/4 canonical
  failures; characterization 7/7 PASS; 178 tests executed.
- V2 `validate-build.ps1`: PASS with the same exact baseline comparison.
- V0 `git diff --check`: PASS (line-ending conversion warnings only).

## Acceptance evidence

- `DlssRrReconstructionBackend` implements `ReconstructionBackend` and is the
  only DLSS-RR facade used by `RtComposite` for availability, quality,
  recommended extent, dispatch, switch-off release and teardown.
- `RtDlssRr` remains the NGX/feature owner, preserving dimension/quality/preset
  recreation and its permanent setup/evaluate failure latch.
- The adapter forwards color, depth, motion, diffuse/specular albedo, normal,
  specular motion, output, extents and matrices in the legacy order.
- Jitter remains negated exactly once by `RtComposite` before dispatch; fresh
  feature creation still requests history reset internally.
- Failed setup/evaluation still returns the raw trace to the established
  fallback upscale path rather than disabling the renderer.
- The priority characterization now follows the adapter and still proves
  DLSS-RR > FSR > XeSS.
- No shader, reconstruction math, quality/preset, feature flags, capability
  policy or hot-path wait-idle changes were made.
