# REMEDIATION-06B — Frame generation ownership extraction

Status: COMPLETED

## Ownership

`FrameGenerationResources` is the production owner of the complete frame-generation domain:

- HUD-less SDR/HDR captures;
- interpolation, backbuffer-copy, and previous-frame images;
- native interpolation, generated-frame UI-composite, and sky-mask pipelines;
- sizing/format metadata, reset and history validity;
- camera discontinuity, jitter, reprojection matrices, failure latches, and native last-use state.

`RtComposite` retains only the existing high-level capture/interpolation routing and a temporary narrow
`GeneratedFrameUiComposer` capability. It no longer owns FG resources or FG temporal state.

## Lifetime and behavior

Normal replacement of published FG images is routed through `FrameTailRetirement`, whose production
implementation uses the persistent encoder's `queueForDestroy` seam. Composite `GraphicsUse` and frame
indices are not treated as GPU completion evidence. Device-idle final teardown remains immediate.

Backend priority, capture timing, HDR/SDR source selection, jitter and reprojection math, camera-history
rules, reset timing, generated-frame ordering, and fallback/failure behavior were preserved. No shader,
descriptor-binding, canonical-baseline, validator, or hot-path `waitIdle` change was made.

## Tests and validation

- Focused `FrameGenerationResourcesTest` and existing `PostProcessingOwnershipTest`: PASS.
- `validate-fast.ps1`: PASS; 262 total, 258 passing, exact 4 canonical failures, 0 unexpected;
  characterization 7/7 PASS.
- `validate-build.ps1`: PASS with the same V1 result and V2 build PASS.

The unit tests cover state/reset and ownership boundaries. Device-level Vulkan execution and encoder
completion remain end-to-end validation responsibilities; they are not claimed as behaviorally proven by
these unit tests.

FINAL remains PENDING. Presentation ownership is intentionally left for REMEDIATION-06C.
