# AER-054 result

## Pre-audit and runtime seam

`RtFsrUpscaler` owns the FidelityFX 3.1 context, reversed-Z flags, quality
mapping, temporal reset and failure latch. `RtComposite` directly owned
selection, camera-discontinuity tracking and dispatch orchestration.

Wrap the existing implementation, move only FSR-specific camera history into
the backend, and preserve reset callback timing through `TemporalState`.

## Validation

- Targeted `FsrUpscalerBackendTest`, `UpscalerBackendTest` and
  `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS, baseline-equivalent exact 4/4 canonical
  failures; characterization 7/7 PASS; 180 tests executed.
- V2 `validate-build.ps1`: PASS with the same exact baseline comparison.
- V0 `git diff --check`: PASS (line-ending conversion warnings only).

## Acceptance evidence

- `FsrUpscalerBackend` implements `UpscalerBackend` and is used by
  `RtComposite` for availability, quality, render extent, dispatch,
  switch-off release, reset and teardown.
- `RtFsrUpscaler` remains the FidelityFX 3.1 context/failure-latch owner;
  HDR, inverted/infinite depth flags and `Float.MAX_VALUE/CAMERA_NEAR` depth
  convention are unchanged.
- The backend owns only FSR camera-discontinuity history. The same `> 32^2`
  test invokes the same-frame `TemporalState` broadcast before evaluation.
- Color, depth, motion, null reactive mask, output, extents, negated jitter and
  vertical FOV are forwarded in the legacy order.
- The priority characterization follows the backend while still proving
  DLSS-RR > FSR > XeSS.
- No shader, FidelityFX math, tuning, capability policy or hot-path wait-idle
  changes were made.
