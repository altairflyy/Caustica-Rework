# AER-053 result

## Pre-audit and plan

The legacy upscale slot has three materially different request shapes: FSR,
XeSS and the native fallback. Selection is already mutually exclusive, while
each implementation supplies availability, render extent, reset, dispatch and
destroy behavior.

Use a generic request type so the contract does not force unused parameters on
any backend. Represent the selected render extent and downstream output
explicitly; runtime adoption remains assigned to AER-054 through AER-056.

## Validation

- Targeted `UpscalerBackendTest`: PASS.
- V1 `validate-fast.ps1`: PASS, baseline-equivalent exact 4/4 canonical
  failures; characterization 7/7 PASS; 179 tests executed.
- V2 `validate-build.ps1`: PASS with the same exact baseline comparison.
- V0 `git diff --check`: PASS (line-ending conversion warnings only).

## Acceptance evidence

- `UpscalerBackend<I>` exposes availability, recommended render extent,
  history reset, execution and destruction.
- The generic request keeps FSR, XeSS and native/off inputs separate rather
  than introducing unused common fields.
- `FrameContext.Extent` makes the display-to-render recommendation explicit;
  `UpscaleResult` requires a concrete downstream output.
- This is intentionally contract-only. AER-054 through AER-056 remain
  responsible for implementation, runtime selection and proving one active
  backend per frame.
- No runtime, shader, upscale math, jitter, reversed-Z, selection, resource
  ownership or hot-path wait-idle changes were made.
