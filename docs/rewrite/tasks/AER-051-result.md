# AER-051 result

## Pre-audit and runtime seam

`RtComposite.ensureOutput(...)` owns the resize/allocation timing, while
`reconstructFrame(...)` owns the established RR > NRD > SVGF selection and
dispatch order. The SVGF backend can therefore own `RtSvgfDenoiser`,
`SvgfResources`, constants and recording logic while those two seams retain
selection and synchronization timing.

The implementation will preserve the exact history parity, previous-camera
delta, barriers, history-feedback copy, previous-guide copies and downstream
image chosen by the legacy path. No shader or tuning change is permitted.

## Validation

- Targeted `ReconstructionBackendTest`, `SvgfResourcesTest`,
  `RtDenoiserShaderRegressionTest` and `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS, baseline-equivalent exact 4/4 canonical
  failures; characterization 7/7 PASS; 177 tests executed.
- V2 `validate-build.ps1`: PASS with the same exact baseline comparison.
- V0 `git diff --check`: PASS (line-ending conversion warnings only).

## Acceptance evidence

- `SvgfReconstructionBackend` implements `ReconstructionBackend` and is the
  production runtime owner of `RtSvgfDenoiser` and `SvgfResources`.
- `RtComposite` retains the established RR > NRD > SVGF selection and calls the
  backend at the same reconstruction pass seam.
- Extent recreation still destroys/reallocates SVGF images after the existing
  synchronization point; descriptor invalidation and logging retain their
  original timing.
- History current/previous selection, parity, camera-forward sign, reset,
  barriers, feedback copy, previous-guide copies and downstream result are
  unchanged. The characterization now follows the new owner without weakening
  those invariants.
- No parallel SVGF pipeline/resource authority remains in `RtComposite`.
- No shader, denoiser math, tuning, selection policy or hot-path wait-idle
  changes were made.
