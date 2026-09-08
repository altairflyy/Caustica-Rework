# AER-056 result

## Pre-audit and runtime seam

The legacy off/native state was represented by a missing optional render-size
result and by an inline fallback blit in `RtComposite`. That conflated a real
1:1 backend with absence/failure of optional backends.

Create an always-available, stateless native backend owning the exact fallback
blit. Every render-size branch must now return an explicit extent.

## Validation

- `NativeUpscalerBackendTest` plus the upscaler contract and rewrite
  characterization tests: PASS.
- V1: BASELINE-EQUIVALENT, exact canonical 4/4 failures; characterization
  7/7 PASS; 182 tests executed.
- V2: PASS.
- `git diff --check`: PASS.
- Shader/math changes: none.
- Native backend selection is explicit; production no longer uses a null
  recommended extent to mean native/off.
