# AER-016 result

## Pre-audit and runtime seam

The existing `SvgfResources` was only a test-visible container. Before this
task, `RtComposite` still owned every SVGF history/moments/filter image, the
previous view-Z/normal images, history parity, `hasHistory`, and the previous
camera position.

The conservative runtime seam is `RtComposite.ensureOutput(...)` for resource
allocation, destruction and resolution-reset state, and the existing SVGF
section of `recordFrame(...)` for dispatch inputs, parity flip and previous
camera snapshot. `RtSvgfDenoiser` remains the dispatch/descriptor owner.

## Runtime ownership

`SvgfResources` is now instantiated by `RtComposite` and owns allocation,
destruction, reset, parity, history validity, previous guide images, and the
previous camera snapshot. `RtComposite` delegates every SVGF resource/state
access through that owner; only `RtSvgfDenoiser` remains outside it for dispatch
registration and descriptor binding.

The current/previous history and moments selection, parity passed to the
denoiser, filter ping-pong, reset-on-recreation, previous view-Z/normal copies,
and camera-forward delta retain the reference order and semantics. No shader,
descriptor layout, denoiser math, or temporal policy changed.

## Validation

- `SvgfResourcesTest`: PASS.
- `RtDenoiserShaderRegressionTest`: PASS.
- `RtRewriteCharacterizationTest`: PASS, 7/7.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`, exact 4/4 frozen
  failures.
- V2 `validate-build.ps1`: PASS.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no shader-source or descriptor-layout changes,
  no SVGF math changes and no new `waitIdle`.

State: DONE.
