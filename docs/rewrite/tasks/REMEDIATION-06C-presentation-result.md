# REMEDIATION-06C — Presentation ownership extraction

Status: COMPLETED

## Ownership

`PostProcessing` is the production owner of HDR/SDR presentation state and resources:

- HDR UI composite pipeline and shared nearest/clamp sampler;
- SDR-to-PQ pipeline and replaceable presentation image;
- per-frame HDR-written state;
- HDR and SDR-to-PQ command recording, swapchain transitions, and semaphore routing;
- presentation resource creation, replacement, and device-idle destruction.

`RtComposite` retains only public routing methods used by the existing presentation hooks. It owns no
presentation image, sampler, pipeline, sizing state, or presentation-specific destruction logic.

Frame generation consumes the narrow `GeneratedFrameUiComposer` capability implemented by
`PostProcessing`; `PostProcessing` does not depend on `FrameGenerationResources`, so no owner cycle exists.

## Lifetime and behavior

Normal replacement of the published SDR-to-PQ image uses `FrameTailRetirement`, backed by the persistent
encoder's `queueForDestroy` seam. Composite `GraphicsUse` is not used as completion evidence, and no new
hot-path `waitIdle` was introduced. Device-idle final teardown remains immediate.

HDR/SDR selection, pre-UI HDR capture timing, HDR UI composition, swapchain barrier ordering, acquire and
present semaphore ordering, Y flip, SDR-to-PQ conversion, generated-frame presentation, and menu/loading
fallback behavior were preserved. No shader, canonical-baseline, validator, ROADMAP, or FINAL-state change
was made.

## Test migration and validation

`PostProcessingOwnershipTest` was migrated after its two new failures were classified as stale structural
assumptions: it required the former no-argument constructor text and the former external
`displayWritten.run()` callback. The migrated assertions retain the same lifecycle ordering and now require
the retirement dependency, internally owned HDR completion state, unique presentation ownership, and the
one-way generated-frame UI capability.

- Focused `PostProcessingOwnershipTest` and `FrameGenerationResourcesTest`: PASS.
- `validate-fast.ps1`: PASS; 263 total, 259 passing, exact 4 canonical failures, 0 unexpected;
  characterization 7/7 PASS.
- `validate-build.ps1`: PASS with the same V1 result and V2 build PASS.

Device-level semaphore completion and presentation output remain end-to-end validation responsibilities;
the structural tests do not claim to prove Vulkan execution.

FINAL remains PENDING.
