# AER-055 result

## Pre-audit and runtime seam

`RtXessUpscaler` already owns the XeSS context, quality mapping, inverted-depth
flag, reset and failure latch. Its runtime option is exposed only when
`RtDeviceBringup` enables the required XeSS feature pair.

Wrap that owner, move XeSS-specific camera history into the backend and retain
the same temporal reset and jitter-suppression timing.

## Validation

- Targeted `XessUpscalerBackendTest`, `UpscalerBackendTest` and
  `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: PASS, baseline-equivalent exact 4/4 canonical
  failures; characterization 7/7 PASS; 181 tests executed.
- V2 `validate-build.ps1`: PASS with the same exact baseline comparison.
- V0 `git diff --check`: PASS (line-ending conversion warnings only).

## Acceptance evidence

- `XessUpscalerBackend` implements `UpscalerBackend` and is used by
  `RtComposite` for availability, quality, render extent, dispatch,
  switch-off release, reset and teardown.
- `RtXessUpscaler` remains the context/failure-latch owner and retains
  `FLAG_INVERTED_DEPTH`; `RtDeviceBringup` still controls support through
  `xessFeaturesEnabled = support.xess` before the option can be enabled.
- The backend owns XeSS camera-discontinuity history and retains the same
  `> 32^2` same-frame reset broadcast before execution.
- Jitter remains image-space as-is and is still zeroed only when SVGF or NRD
  already integrated it.
- No shader, XeSS algorithm, quality, capability policy or hot-path wait-idle
  changes were made.
