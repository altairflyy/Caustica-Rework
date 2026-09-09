# REMEDIATION-05A result — Move NRD combine ownership

Status: COMPLETED

## Ownership

`ExperimentalNrdBackend` now owns the complete `RtNrdCombinePipeline` lifecycle: lazy creation,
binding invalidation and setup, combine dispatch, readiness through pipeline presence, and destruction.
`RtComposite` retains only backend orchestration and supplies immutable borrowed `NrdFrameViews`.
The backend has no dependency on `RtComposite` or on a frame-resource owner.

## Semantic preservation

- NRD denoise still precedes combine/remodulation.
- Descriptor order, formats, shader, 16x16 dispatch geometry, denoising range and fallback behavior are
  unchanged.
- The same combined image is published to the reconstruction/upscale path.
- NRD selection and availability semantics remain unchanged.
- No shader or barrier semantics changed.

## Tests and validation

- Added extraction-stable reflection coverage proving combine ownership resides in
  `ExperimentalNrdBackend`, not `RtComposite`, with no reverse owner dependency.
- `validate-fast.ps1`: PASS; 254 tests, exact 4/4 canonical failures, 0 unexpected failures,
  characterization 7/7 PASS.
- `git diff --check`: PASS.

Device-level Vulkan creation, descriptor update, dispatch and destruction remain covered by canonical
validation rather than independently injected behavioral tests. This result does not claim FINAL PASS.
